Estamos em um marco importante: o núcleo OMR dinâmico está concluído e validado. A branch publicada está em:

```text
feature/omr-dinamico-1-90
commit 2f4f198
```

### O que já está pronto

- cartões de 1 a 90 questões;
- 4 alternativas A–D ou 5 alternativas A–E;
- quantidade automática ou manual de itens por coluna;
- largura ajustada à quantidade real de colunas;
- marcadores, bolhas e textos com dimensões físicas constantes;
- geração e salvamento em SVG;
- cadastro persistente de vários gabaritos;
- identificação manual do aluno;
- captura OpenCV, correção e histórico persistente;
- leitura controlada `v1`, `v2` e `v3`;
- leitura manual validada no emulador:
    - 2 questões, uma coluna;
    - 7 questões, duas colunas configuradas;
- última suíte completa: 150 testes instrumentados sem falhas;
- seis novos cenários `v3` também passaram dentro dos 16 testes do pipeline.

### O que ainda não foi construído

O app ainda não possui entidades escolares completas para:

- Prova;
- Turma;
- cadastro permanente de alunos;
- importação CSV;
- vínculo Prova–Turma;
- situação de cada aluno naquela aplicação;
- visão consolidada dos resultados;
- PDF da turma;
- QR Code da prova/aplicação;
- autenticação e sincronização entre professores.

Atualmente, `OmrStudentIdentity` funciona como uma fotografia do aluno vinculada ao resultado. Ele não substitui um cadastro real de aluno e turma.

### Próximo passo técnico

Antes de desenvolver turmas, devemos introduzir a entidade **Prova**. Ela será o ponto central que liga:

```text
Prova → gabarito/layout → turma → alunos → correções → relatório
```

A sequência mais segura será:

1. Consolidar a versão atual na `master`.
2. Criar a branch `feature/provas-turmas-offline`.
3. Criar o domínio de **Prova**, separado de `OmrAnswerKeyDefinition`.
4. Criar **Turma**, **Aluno** e matrícula na turma.
5. Persistir tudo localmente em SQLite.
6. Permitir cadastro manual e importação CSV.
7. Criar o vínculo **Prova–Turma**.
8. Exibir alunos pendentes/corrigidos e suas notas.
9. Gerar tabela e PDF.
10. Acrescentar QR Code.
11. Adicionar autenticação e sincronização em uma camada posterior.

Essa separação preservará a versão offline e permitirá adicionar o perfil autenticado futuramente sem reconstruir o núcleo OMR.

### Ação imediata

A tag `v0.2.0` ainda não aparece no repositório remoto. Primeiro preserve o marco atual:

```powershell
git tag -a v0.2.0-omr-dinamico-flexivel 2f4f198 -m "OMR dinamico flexivel de 1 a 90 questoes"
git push origin v0.2.0-omr-dinamico-flexivel
git show --no-patch --oneline --decorate v0.2.0-omr-dinamico-flexivel
```

Depois dessa confirmação, integraremos a branch à `master` e iniciaremos o núcleo de **Prova** em uma branch nova.