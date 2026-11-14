# C&C Amplificador

App Android de **amplificador + equalizador** para melhorar o som de fones e caixas **Bluetooth** (funciona também em fone com fio e alto-falante).

## Baixar
Abra **Releases** neste repositório no celular e instale o `CC-Amplificador-v1.0.X.apk` mais recente (permita "instalar apps desconhecidos"). Atualizações instalam por cima, sem perder ajustes.

## Recursos
- **Amplificação** de 0 a +15 dB com **limitador anti-distorção**
- **Equalizador gráfico de 10 bandas** (31 Hz – 16 kHz, ±12 dB) — arraste os pontos
- Presets: Plano, Bluetooth claro, Graves fortes, Vocal/Podcast, Rock, Pop, Sertanejo, Eletrônica, Clássica, Agudos, Filmes
- **Reforço de graves** e **som espacial (3D)**
- **Perfil automático por dispositivo**: cada fone/caixa Bluetooth lembra seu próprio ajuste e é aplicado ao conectar
- Roda em segundo plano (notificação com botão *Desligar*)
- Aparece no botão "Equalizador" de players como Spotify e YouTube Music

## Como funciona
- Android 9+: usa `DynamicsProcessing` (EQ de 10 bandas + ganho de entrada + limitador).
- Android 8: usa `Equalizer` + `LoudnessEnhancer` + `BassBoost` do sistema.
- Os efeitos são aplicados na mixagem global (sessão 0) e também em cada player que anuncia sua sessão de áudio.

## Limitações
- Alguns fabricantes bloqueiam efeitos na mixagem global. Nesse caso o app funciona nos players que anunciam a sessão (Spotify, YouTube Music, Deezer, Poweramp…). Abra o player **depois** de ligar o amplificador.
- Desative outros equalizadores (Dolby, Samsung Sound/Adapt Sound, Wavelet) para evitar conflito.
- Volume alto por tempo prolongado pode prejudicar a audição.

## Compilar
Push em `main` → GitHub Actions gera o APK e publica em Releases. Localmente: `./gradlew assembleDebug` (JDK 17 + Android SDK 35).
