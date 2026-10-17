package com.example.leitorgabaritoomr.infrastructure.export;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.CRC32;

/**
 * Rasteriza para PNG exatamente o subconjunto SVG emitido por
 * {@link OmrSheetSvgGenerator}.
 *
 * O gerador SVG permanece como fonte unica da geometria OMR. Este componente
 * somente converte seus retangulos, circulos e textos em pixels, em fundo
 * branco e com proporcao preservada. O resultado inclui o bloco PNG pHYs com
 * a densidade real de impressao.
 */
public final class OmrSheetPngRenderer {

    public static final int DEFAULT_DOTS_PER_INCH = 300;
    public static final int MINIMUM_DOTS_PER_INCH = 150;
    public static final int MAXIMUM_DOTS_PER_INCH = 300;

    private static final double MILLIMETERS_PER_INCH = 25.4;
    private static final long MAXIMUM_PIXEL_COUNT = 8_000_000L;

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    private final int dotsPerInch;

    public OmrSheetPngRenderer() {
        this(DEFAULT_DOTS_PER_INCH);
    }

    public OmrSheetPngRenderer(int dotsPerInch) {
        if (dotsPerInch < MINIMUM_DOTS_PER_INCH
                || dotsPerInch > MAXIMUM_DOTS_PER_INCH) {
            throw new IllegalArgumentException(
                    "A resolucao PNG deve estar entre "
                            + MINIMUM_DOTS_PER_INCH
                            + " e "
                            + MAXIMUM_DOTS_PER_INCH
                            + " dpi."
            );
        }
        this.dotsPerInch = dotsPerInch;
    }

    public OmrSheetPngRenderResult write(
            OmrSheetSvgDocument source,
            OutputStream destination
    ) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException(
                    "O documento SVG de origem e obrigatorio."
            );
        }
        if (destination == null) {
            throw new IllegalArgumentException(
                    "O destino do PNG e obrigatorio."
            );
        }

        int widthPixels = millimetersToPixels(
                source.getOutputWidthMillimeters()
        );
        int heightPixels = millimetersToPixels(
                source.getOutputHeightMillimeters()
        );
        validateBitmapSize(widthPixels, heightPixels);

        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(
                    widthPixels,
                    heightPixels,
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            float scale = Math.min(
                    widthPixels / (float) source.getViewWidth(),
                    heightPixels / (float) source.getViewHeight()
            );
            float translatedX = (
                    widthPixels - (float) source.getViewWidth() * scale
            ) / 2.0f;
            float translatedY = (
                    heightPixels - (float) source.getViewHeight() * scale
            ) / 2.0f;

            canvas.save();
            canvas.translate(translatedX, translatedY);
            canvas.scale(scale, scale);
            drawGeneratedSvg(canvas, source.getContent());
            canvas.restore();

            ByteArrayOutputStream encoded =
                    new ByteArrayOutputStream(256 * 1024);

            if (!bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    encoded
            )) {
                throw new IOException(
                        "O Android nao conseguiu codificar o bitmap como PNG."
                );
            }

            writeWithPhysicalResolution(
                    encoded.toByteArray(),
                    destination,
                    dotsPerInch
            );
            destination.flush();

            return new OmrSheetPngRenderResult(
                    pngFileName(source.getSuggestedFileName()),
                    source.getTemplateId(),
                    source.getTemplateVersion(),
                    source.getQuestionCount(),
                    widthPixels,
                    heightPixels,
                    dotsPerInch,
                    source.getOutputWidthMillimeters(),
                    source.getOutputHeightMillimeters()
            );

        } catch (XmlPullParserException exception) {
            throw new IOException(
                    "O SVG gerado nao pode ser interpretado.",
                    exception
            );
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    public int getDotsPerInch() {
        return dotsPerInch;
    }

    private int millimetersToPixels(double millimeters) {
        return Math.max(
                1,
                (int) Math.round(
                        millimeters
                                * dotsPerInch
                                / MILLIMETERS_PER_INCH
                )
        );
    }

    private void validateBitmapSize(int width, int height) {
        long pixelCount = (long) width * (long) height;
        if (pixelCount <= 0L || pixelCount > MAXIMUM_PIXEL_COUNT) {
            throw new IllegalArgumentException(
                    "O cartao geraria "
                            + pixelCount
                            + " pixels; o limite seguro e "
                            + MAXIMUM_PIXEL_COUNT
                            + "."
            );
        }
    }

    private void drawGeneratedSvg(
            Canvas canvas,
            String svg
    ) throws IOException, XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(svg));

        Deque<GroupState> groups = new ArrayDeque<>();
        groups.push(GroupState.root());

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();

                if ("g".equals(name)) {
                    groups.push(GroupState.child(
                            groups.peek(),
                            attribute(parser, "transform"),
                            attribute(parser, "fill")
                    ));
                } else if ("rect".equals(name)) {
                    drawRect(canvas, parser, groups.peek());
                } else if ("circle".equals(name)) {
                    drawCircle(canvas, parser, groups.peek());
                } else if ("text".equals(name)) {
                    drawText(canvas, parser, groups.peek());
                } else if (!"svg".equals(name)
                        && !"title".equals(name)
                        && !"desc".equals(name)) {
                    throw new IOException(
                            "Elemento SVG nao suportado: " + name
                    );
                }

            } else if (event == XmlPullParser.END_TAG
                    && "g".equals(parser.getName())) {
                if (groups.size() <= 1) {
                    throw new IOException(
                            "A hierarquia de grupos do SVG e invalida."
                    );
                }
                groups.pop();
            }

            event = parser.next();
        }

        if (groups.size() != 1) {
            throw new IOException(
                    "O SVG terminou com grupos ainda abertos."
            );
        }
    }

    private void drawRect(
            Canvas canvas,
            XmlPullParser parser,
            GroupState group
    ) throws IOException {
        rejectElementTransform(parser);
        float left = number(parser, "x") + group.translateX;
        float top = number(parser, "y") + group.translateY;
        float right = left + number(parser, "width");
        float bottom = top + number(parser, "height");

        String fill = resolvedFill(parser, group);
        if (!"none".equals(fill)) {
            Paint paint = fillPaint(fill);
            canvas.drawRect(left, top, right, bottom, paint);
        }

        String stroke = attribute(parser, "stroke");
        if (stroke != null && !"none".equals(stroke)) {
            Paint paint = strokePaint(
                    stroke,
                    optionalNumber(parser, "stroke-width", 1.0f)
            );
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }

    private void drawCircle(
            Canvas canvas,
            XmlPullParser parser,
            GroupState group
    ) throws IOException {
        rejectElementTransform(parser);
        float centerX = number(parser, "cx") + group.translateX;
        float centerY = number(parser, "cy") + group.translateY;
        float radius = number(parser, "r");

        String fill = resolvedFill(parser, group);
        if (!"none".equals(fill)) {
            canvas.drawCircle(
                    centerX,
                    centerY,
                    radius,
                    fillPaint(fill)
            );
        }

        String stroke = attribute(parser, "stroke");
        if (stroke != null && !"none".equals(stroke)) {
            canvas.drawCircle(
                    centerX,
                    centerY,
                    radius,
                    strokePaint(
                            stroke,
                            optionalNumber(
                                    parser,
                                    "stroke-width",
                                    1.0f
                            )
                    )
            );
        }
    }

    private void drawText(
            Canvas canvas,
            XmlPullParser parser,
            GroupState group
    ) throws IOException, XmlPullParserException {
        rejectElementTransform(parser);
        float x = number(parser, "x") + group.translateX;
        float y = number(parser, "y") + group.translateY;
        float fontSize = optionalNumber(parser, "font-size", 16.0f);
        String anchor = attribute(parser, "text-anchor");
        String weight = attribute(parser, "font-weight");
        String fill = resolvedFill(parser, group);
        String textLengthValue = attribute(parser, "textLength");
        String text = parser.nextText();

        Paint paint = fillPaint(fill);
        paint.setTextSize(fontSize);
        paint.setTypeface(Typeface.create(
                "sans-serif",
                isBold(weight) ? Typeface.BOLD : Typeface.NORMAL
        ));
        paint.setTextAlign(textAlign(anchor));

        if (textLengthValue != null && !text.isEmpty()) {
            float requestedLength = parseNumber(
                    "textLength",
                    textLengthValue
            );
            float measuredLength = paint.measureText(text);
            if (measuredLength > 0.0f) {
                paint.setTextScaleX(requestedLength / measuredLength);
            }
        }

        canvas.drawText(text, x, y, paint);
    }

    private Paint fillPaint(String color) throws IOException {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(parseColor(color));
        return paint;
    }

    private Paint strokePaint(
            String color,
            float width
    ) throws IOException {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(parseColor(color));
        return paint;
    }

    private int parseColor(String value) throws IOException {
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "Cor SVG nao suportada: " + value,
                    exception
            );
        }
    }

    private String resolvedFill(
            XmlPullParser parser,
            GroupState group
    ) {
        String fill = attribute(parser, "fill");
        return fill == null ? group.fill : fill;
    }

    private Paint.Align textAlign(String anchor) {
        if ("middle".equals(anchor)) {
            return Paint.Align.CENTER;
        }
        if ("end".equals(anchor)) {
            return Paint.Align.RIGHT;
        }
        return Paint.Align.LEFT;
    }

    private boolean isBold(String weight) {
        if (weight == null) {
            return false;
        }
        if ("bold".equalsIgnoreCase(weight)) {
            return true;
        }
        try {
            return Integer.parseInt(weight) >= 600;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private float number(
            XmlPullParser parser,
            String attributeName
    ) throws IOException {
        String value = attribute(parser, attributeName);
        if (value == null) {
            throw new IOException(
                    "Atributo SVG obrigatorio ausente: " + attributeName
            );
        }
        return parseNumber(attributeName, value);
    }

    private float optionalNumber(
            XmlPullParser parser,
            String attributeName,
            float defaultValue
    ) throws IOException {
        String value = attribute(parser, attributeName);
        return value == null
                ? defaultValue
                : parseNumber(attributeName, value);
    }

    private float parseNumber(
            String attributeName,
            String value
    ) throws IOException {
        String normalized = value.trim();
        if (normalized.endsWith("mm")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 2
            );
        }
        try {
            float parsed = Float.parseFloat(normalized);
            if (!Float.isFinite(parsed)) {
                throw new NumberFormatException("not finite");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException(
                    "Valor SVG invalido em "
                            + attributeName
                            + ": "
                            + value,
                    exception
            );
        }
    }

    private String attribute(
            XmlPullParser parser,
            String name
    ) {
        return parser.getAttributeValue(null, name);
    }

    private void rejectElementTransform(XmlPullParser parser)
            throws IOException {
        String transform = attribute(parser, "transform");
        if (transform != null && !transform.trim().isEmpty()) {
            throw new IOException(
                    "Transformacao SVG fora de grupo nao suportada: "
                            + transform
            );
        }
    }

    private String pngFileName(String svgFileName) {
        return svgFileName.substring(
                0,
                svgFileName.length() - 4
        ) + ".png";
    }

    private void writeWithPhysicalResolution(
            byte[] encodedPng,
            OutputStream destination,
            int dpi
    ) throws IOException {
        validatePngHeader(encodedPng);

        int pixelsPerMeter = (int) Math.round(
                dpi / 0.0254
        );
        byte[] physicalData = new byte[9];
        writeInt(physicalData, 0, pixelsPerMeter);
        writeInt(physicalData, 4, pixelsPerMeter);
        physicalData[8] = 1;

        destination.write(PNG_SIGNATURE);

        int offset = PNG_SIGNATURE.length;
        boolean physicalChunkWritten = false;
        boolean endChunkFound = false;

        while (offset < encodedPng.length) {
            if (offset + 12 > encodedPng.length) {
                throw new IOException("Bloco PNG truncado.");
            }

            int dataLength = readInt(encodedPng, offset);
            long chunkLength = 12L + dataLength;
            long nextOffset = offset + chunkLength;
            if (dataLength < 0 || nextOffset > encodedPng.length) {
                throw new IOException("Comprimento de bloco PNG invalido.");
            }

            String type = new String(
                    encodedPng,
                    offset + 4,
                    4,
                    StandardCharsets.US_ASCII
            );

            if (!"pHYs".equals(type)) {
                destination.write(
                        encodedPng,
                        offset,
                        (int) chunkLength
                );
            }

            if ("IHDR".equals(type)) {
                writeChunk(destination, "pHYs", physicalData);
                physicalChunkWritten = true;
            }

            offset = (int) nextOffset;
            if ("IEND".equals(type)) {
                endChunkFound = true;
                break;
            }
        }

        if (!physicalChunkWritten || !endChunkFound
                || offset != encodedPng.length) {
            throw new IOException("Estrutura de blocos PNG invalida.");
        }
    }

    private void validatePngHeader(byte[] png) throws IOException {
        if (png.length < PNG_SIGNATURE.length + 25) {
            throw new IOException("O PNG codificado esta incompleto.");
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (png[index] != PNG_SIGNATURE[index]) {
                throw new IOException("Assinatura PNG invalida.");
            }
        }
        String firstType = new String(
                png,
                PNG_SIGNATURE.length + 4,
                4,
                StandardCharsets.US_ASCII
        );
        if (!"IHDR".equals(firstType)) {
            throw new IOException("O primeiro bloco PNG nao e IHDR.");
        }
        int dataLength = readInt(png, PNG_SIGNATURE.length);
        long afterIhdr = (long) PNG_SIGNATURE.length + 12L + dataLength;
        if (dataLength != 13 || afterIhdr > png.length) {
            throw new IOException("O bloco IHDR do PNG e invalido.");
        }
    }

    private void writeChunk(
            OutputStream destination,
            String type,
            byte[] data
    ) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        writeInt(destination, data.length);
        destination.write(typeBytes);
        destination.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(destination, (int) crc.getValue());
    }

    private int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private void writeInt(OutputStream output, int value)
            throws IOException {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static final class GroupState {
        private final float translateX;
        private final float translateY;
        private final String fill;

        private GroupState(
                float translateX,
                float translateY,
                String fill
        ) {
            this.translateX = translateX;
            this.translateY = translateY;
            this.fill = fill;
        }

        private static GroupState root() {
            return new GroupState(0.0f, 0.0f, "#000000");
        }

        private static GroupState child(
                GroupState parent,
                String transform,
                String fill
        ) throws IOException {
            float x = parent.translateX;
            float y = parent.translateY;

            if (transform != null && !transform.trim().isEmpty()) {
                String normalized = transform.trim();
                if (!normalized.startsWith("translate(")
                        || !normalized.endsWith(")")) {
                    throw new IOException(
                            "Transformacao SVG nao suportada: " + transform
                    );
                }
                String parameters = normalized.substring(
                        "translate(".length(),
                        normalized.length() - 1
                ).trim();
                String[] values = parameters.split("[\\s,]+", -1);
                if (values.length < 1 || values.length > 2) {
                    throw new IOException(
                            "Translacao SVG invalida: " + transform
                    );
                }
                try {
                    float translatedX = Float.parseFloat(values[0]);
                    float translatedY = values.length == 2
                            ? Float.parseFloat(values[1])
                            : 0.0f;
                    if (!Float.isFinite(translatedX)
                            || !Float.isFinite(translatedY)) {
                        throw new NumberFormatException("not finite");
                    }
                    x += translatedX;
                    if (values.length == 2) {
                        y += translatedY;
                    }
                } catch (NumberFormatException exception) {
                    throw new IOException(
                            "Translacao SVG invalida: " + transform,
                            exception
                    );
                }
            }

            return new GroupState(
                    x,
                    y,
                    fill == null ? parent.fill : fill
            );
        }
    }
}
