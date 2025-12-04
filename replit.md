# Hannsapp - FPS Counter & System Monitor

## Visão Geral
Hannsapp é um aplicativo Android nativo para monitoramento de FPS e informações do sistema. O app exibe um overlay flutuante com contador de FPS em tempo real, uso de memória, CPU e outras informações do sistema.

## Stack Tecnológica
- **Linguagem**: Kotlin + Java
- **Plataforma**: Android (SDK 24+)
- **Build System**: Gradle 8.4
- **UI**: Material Design 3
- **Bibliotecas**:
  - AndroidX (AppCompat, ConstraintLayout, Navigation, Preference, Work)
  - Shizuku API (para comandos privilegiados)
  - libsu (para comandos root)
  - MPAndroidChart (gráficos)
  - Lottie (animações)
  - Gson (serialização JSON)

## Estrutura do Projeto
```
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/hannsapp/fpscounter/
│   │   │   ├── HannsApplication.kt          # Application class
│   │   │   ├── ui/                          # Activities
│   │   │   │   ├── MainActivity.kt          # Tela principal
│   │   │   │   ├── ConnectionActivity.kt    # Conexão Wi-Fi/Shizuku
│   │   │   │   ├── SettingsActivity.kt      # Configurações
│   │   │   │   ├── AppSelectionActivity.kt  # Seleção de apps
│   │   │   │   ├── OverlayCustomizationActivity.kt
│   │   │   │   └── SystemInfoActivity.kt
│   │   │   ├── services/                    # Serviços
│   │   │   │   ├── FpsMonitorService.kt     # Monitor de FPS
│   │   │   │   ├── FpsOverlayService.kt     # Overlay flutuante
│   │   │   │   ├── AdbConnectionService.kt  # Conexão ADB
│   │   │   │   ├── ShizukuService.kt        # Integração Shizuku
│   │   │   │   ├── FpsAccessibilityService.kt
│   │   │   │   └── SystemInfoCollector.kt
│   │   │   ├── adapters/
│   │   │   ├── data/                        # Models e Preferences
│   │   │   ├── receivers/
│   │   │   └── utils/                       # Utilitários
│   │   └── res/                             # Recursos Android
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── Scripts de Build:
    ├── setup_android_sdk.sh    # Instala Android SDK
    ├── build_apk.sh           # Compila o APK
    ├── install_apk.sh         # Instala no dispositivo
    └── adb_connect.sh         # Conecta via Wi-Fi debug
```

## Como Compilar

### 1. Configurar Android SDK
```bash
./setup_android_sdk.sh
```

### 2. Compilar APK Debug
```bash
./build_apk.sh debug
```

### 3. Compilar APK Release
```bash
./build_apk.sh release
```

### 4. Conectar Dispositivo (Wi-Fi)
```bash
./adb_connect.sh pair <IP:PORT> <CODE>
./adb_connect.sh connect <IP:PORT>
```

### 5. Instalar no Dispositivo
```bash
./install_apk.sh
```

## Funcionalidades
1. **Contador de FPS** - Monitoramento em tempo real
2. **Overlay Flutuante** - Exibição sobre outros apps
3. **Conexão Wi-Fi Debug** - Pareamento via código
4. **Conexão Shizuku** - Alternativa para comandos privilegiados
5. **Monitor de Sistema** - Memória, CPU, bateria, temperatura
6. **Seleção de Apps** - Escolha quais apps monitorar
7. **Personalização** - Posição, tamanho, cor, opacidade

## Permissões Necessárias
- `SYSTEM_ALERT_WINDOW` - Overlay sobre apps
- `PACKAGE_USAGE_STATS` - Dados de uso
- `FOREGROUND_SERVICE` - Serviços em background
- `POST_NOTIFICATIONS` - Notificações
- `INTERNET` / `ACCESS_NETWORK_STATE` - Conexão Wi-Fi

## Notas
- Requer dispositivo Android 7.0+ (API 24)
- Para recursos avançados, use Shizuku ou root
- O APK compilado estará em `output/Hannsapp-debug.apk`
