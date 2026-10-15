package com.example.leitorgabaritoomr.application.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;

import org.junit.Test;

public final class OmrPublishedFlexibleLayoutV4ResolverTest {

    private static final double DELTA = 0.000001;

    private final OmrPublishedLayoutResolver resolver =
            new OmrPublishedLayoutResolver();

    @Test
    public void resolvesCompactV4WithItsExactGeometry() {
        OmrLayoutDefinition layout = resolver.resolve(
                "omr-flex-ae-q045-r009",
                4,
                45
        );

        assertEquals(4, layout.getVersion());
        assertEquals(45, layout.getQuestionCount());
        assertEquals(5, layout.getBlockCount());
        assertEquals(596, layout.getCanonicalHeight());
        assertEquals(110.0,
                layout.getAllOptions().get(0).getCenter().getY()
                        * layout.getCanonicalHeight(),
                DELTA);
    }

    @Test
    public void continuesResolvingTheOldV3Geometry() {
        OmrLayoutDefinition layout = resolver.resolve(
                "omr-flex-ae-q045-r009",
                3,
                45
        );

        assertEquals(3, layout.getVersion());
        assertEquals(676, layout.getCanonicalHeight());
        assertEquals(160.0,
                layout.getAllOptions().get(0).getCenter().getY()
                        * layout.getCanonicalHeight(),
                DELTA);
    }

    @Test
    public void rejectsUnknownVersionInsteadOfGuessingGeometry() {
        try {
            resolver.resolve(
                    "omr-flex-ae-q045-r009",
                    5,
                    45
            );
            fail("Era esperada uma versão desconhecida.");
        } catch (IllegalArgumentException expected) {
            // Contrato esperado.
        }
    }

    @Test
    public void rejectsQuestionCountThatDisagreesWithIdentity() {
        try {
            resolver.resolve(
                    "omr-flex-ae-q045-r009",
                    4,
                    44
            );
            fail("Era esperada uma identidade incompatível.");
        } catch (IllegalArgumentException expected) {
            // Contrato esperado.
        }
    }
}
