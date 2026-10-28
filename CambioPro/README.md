# Câmbio Pro

App Android nativo (Kotlin, Jetpack Compose, Material 3, MVVM) para cotações de criptomoedas, dólar e conversão de moedas. Informativo: sem login, sem negociação, sem carteira.

Logo: `app/src/main/res/drawable-nodpi/` (ícone adaptativo `ic_launcher_fg.png` e `logo_cambio.png`, usado na tela Sobre).

## Estado real deste projeto
- O código-fonte foi escrito por completo, **mas não foi compilado nem testado** no ambiente em que foi gerado (sem Android SDK, sem Gradle, sem acesso à internet). Não há APK neste pacote.
- Erros de compilação são possíveis. Corrija-os ao abrir no Android Studio ou veja a saída do build.

## Como compilar
**Opção A, Android Studio (recomendado):** abra a pasta, aguarde o sync (ele cria o Gradle Wrapper), depois `Build > Build Bundle(s)/APK(s) > Build APK(s)`.

**Opção B, linha de comando:** JDK 17, Android SDK (plataforma 34) e Gradle 8.9:
```
gradle testDebugUnitTest assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`

**Opção C, GitHub Actions:** suba o projeto para um repositório; `.github/workflows/build-apk.yml` roda os testes, gera o APK e o publica como artefato.

O APK de release usa a **assinatura de teste (debug)**: instala em qualquer aparelho, mas **não serve para a Play Store**. Para publicar, configure sua própria keystore.

## Instalar no Android
Copie o `.apk`, abra-o no aparelho e permita "instalar apps desconhecidos". Ou: `adb install app-release.apk`.

## Fontes de dados
- **CoinGecko** (`https://api.coingecko.com/api/v3/`), API pública sem chave, com limite por IP compartilhado. Preços, variação 24 h, capitalização, volume, máx/mín, gráficos e busca.
- **Frankfurter v1** (`https://api.frankfurter.dev/v1/`), câmbio de referência do BCE, atualizado uma vez por dia útil. A v1 está marcada como descontinuada, mas continua funcionando segundo o site.
- Câmbio de referência não é cotação de compra/venda de banco nem dólar turismo; não há fonte para isso no app.

## Limitações
- O limite do CoinGecko sem chave pode gerar erro 429; o app usa cache, intervalo mínimo de 30 s entre atualizações forçadas e exibe o último dado com aviso.
- Atualização em segundo plano (WorkManager) tem mínimo de 15 min e pode ser adiada pelo Android.
- Câmbio de moedas tradicionais não tem variação de 24 h; usa-se a variação contra o dia útil anterior.
- Cruzamentos (ex.: BTC→EUR) passam por USD e são aproximações de referência.
- Sem imagens/ícones de moedas (sem biblioteca de imagens). Minificação (R8) desativada.

## Trocar fontes / chave opcional
- Endpoints: `data/remote/Apis.kt` e `di/AppContainer.kt`; mapeamento em `data/MarketRepository.kt`.
- Chave demo do CoinGecko (opcional): em Ajustes, salva só no aparelho. Nenhuma chave vai dentro do APK.

## Estrutura
`data/` (remote, local, repositório), `domain/` (modelos, conversor, formatação), `ui/` (tema, componentes, telas, ViewModels), `navigation/`, `di/`, `work/`. Testes: `src/test` (JUnit) e `src/androidTest` (smoke de navegação).

## Testes escritos (não executados)
Conversões (USD↔BRL, cripto↔cripto, inversão, zero, negativo, vazio, decimais, sem cotação, overflow), formatação, histórico (limite 100), repositório (cache, TTL, falha de rede, resposta inválida, retentativas, deduplicação), persistência de favoritos/histórico/configurações e cache em arquivo.
