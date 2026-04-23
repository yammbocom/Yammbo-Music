# Code Signing — Yammbo Music Desktop

Hasta que firmemos los instaladores, los usuarios verán advertencias del sistema operativo al abrir los `.msi`, `.dmg` o `.deb` descargados de GitHub Releases:

- **Windows**: SmartScreen *"Windows protected your PC — Don't run"* con un botón "More info → Run anyway"
- **macOS**: *"YammboMusic can't be opened because Apple cannot check it for malicious software"*
- **Linux**: sin firma estándar del ecosistema; `.deb` unsigned funciona pero los repos oficiales lo rechazarían

Esto NO impide instalar, pero mata conversión. La solución es firmar cada instalador con un certificado de su plataforma. Este doc explica el costo, flujo y cómo lo cableamos en CI.

---

## Windows

### Qué comprar

Un certificado **Code Signing** (no EV salvo que quieras eliminar SmartScreen de inmediato — EV es ~3x más caro y exige hardware token FIPS).

| Tipo | Precio anual | SmartScreen | Entrega |
|---|---|---|---|
| OV (standard) | ~$200–$300 | Warning desaparece después de "build reputation" (~500+ descargas firmadas) | Archivo `.pfx` |
| EV | ~$500–$700 | Warning desaparece instantáneamente | Hardware token USB (HSM) |

Proveedores habituales: [SSL.com](https://www.ssl.com/certificates/code-signing/), [Sectigo](https://sectigostore.com/code-signing-certificates.aspx), [DigiCert](https://www.digicert.com/signing/code-signing-certificates).

Recomendación: empezar con **OV** — el bump de $400/año por EV sólo tiene sentido con volumen >10k descargas/mes.

### Configurar secrets en GitHub

Después de recibir el `.pfx`:

```bash
# Codifica a base64 (línea única)
base64 -w 0 cert.pfx > cert.b64
# Pega el contenido en Settings → Secrets → Actions → New secret
```

Secrets necesarios:

| Secret | Valor |
|---|---|
| `WINDOWS_CERT_PFX_BASE64` | Contenido del `.pfx` codificado en base64 |
| `WINDOWS_CERT_PASSWORD` | Contraseña del `.pfx` |

### Paso de firma en el workflow

Añadir después de "Build installer" en `.github/workflows/desktop-release.yml`, sólo en el job Windows:

```yaml
- name: Sign .msi
  if: matrix.os == 'windows-latest' && env.CERT != ''
  env:
    CERT: ${{ secrets.WINDOWS_CERT_PFX_BASE64 }}
    PASS: ${{ secrets.WINDOWS_CERT_PASSWORD }}
  shell: bash
  run: |
    echo "$CERT" | base64 -d > cert.pfx
    # signtool comes with Windows SDK on windows-latest runner
    SIGNTOOL=$(find "/c/Program Files (x86)/Windows Kits/10/bin" -name signtool.exe | sort -r | head -n1)
    "$SIGNTOOL" sign \
      /f cert.pfx \
      /p "$PASS" \
      /tr http://timestamp.digicert.com \
      /td sha256 \
      /fd sha256 \
      /d "Yammbo Music" \
      /du "https://music.yammbo.com" \
      "${{ steps.artifact.outputs.path }}"
    rm cert.pfx
    "$SIGNTOOL" verify /pa "${{ steps.artifact.outputs.path }}"
```

La cláusula `env.CERT != ''` hace que el paso sea no-op mientras no tengas el cert configurado.

---

## macOS

### Qué comprar

Una cuenta **Apple Developer Program** — $99/año (cuenta individual) o $299/año (Enterprise). Te da:

1. **Developer ID Application certificate** — firma `.app` para distribución fuera del App Store
2. **Developer ID Installer certificate** — firma `.dmg` / `.pkg`
3. Acceso a la API de **notarización** (gratuito una vez tienes la cuenta)

Sin cuenta no hay forma de eliminar el warning de macOS Gatekeeper. No hay alternativa self-signed.

### Configurar secrets en GitHub

Exporta ambos certificados a un único `.p12` (Keychain Access → File → Export Items, con todo el chain incluido).

| Secret | Valor |
|---|---|
| `MACOS_CERT_P12_BASE64` | `base64 -w 0 cert.p12` |
| `MACOS_CERT_PASSWORD` | Contraseña del `.p12` |
| `APPLE_ID` | Tu email de Apple Developer |
| `APPLE_APP_SPECIFIC_PASSWORD` | Password generado en appleid.apple.com → Sign-in and Security → App-Specific Passwords |
| `APPLE_TEAM_ID` | Team ID (10 caracteres) — lo ves en developer.apple.com/account |

### Pasos en el workflow

Añadir al job macOS:

```yaml
- name: Import signing certificate
  if: matrix.os == 'macos-latest' && env.CERT != ''
  env:
    CERT: ${{ secrets.MACOS_CERT_P12_BASE64 }}
    PASS: ${{ secrets.MACOS_CERT_PASSWORD }}
  run: |
    echo "$CERT" | base64 -d > cert.p12
    security create-keychain -p actions build.keychain
    security default-keychain -s build.keychain
    security unlock-keychain -p actions build.keychain
    security import cert.p12 -k build.keychain -P "$PASS" -T /usr/bin/codesign
    security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k actions build.keychain
    rm cert.p12
```

Compose Desktop reads the signing identity via `nativeDistributions.macOS.signing.sign = true` + environment variables or directly via Gradle properties. Easiest path is to pass the identity as Gradle property:

```yaml
- name: Build signed .dmg
  if: matrix.os == 'macos-latest'
  shell: bash
  run: |
    if [ -n "${{ secrets.APPLE_TEAM_ID }}" ]; then
      ./gradlew :composeApp:packageReleaseDmg \
        -Pcompose.desktop.mac.sign=true \
        -Pcompose.desktop.mac.signing.identity="Developer ID Application: Yammbo LLC (${{ secrets.APPLE_TEAM_ID }})" \
        --no-daemon --stacktrace
    else
      ./gradlew :composeApp:packageReleaseDmg --no-daemon --stacktrace
    fi

- name: Notarize .dmg
  if: matrix.os == 'macos-latest' && env.APPLE_ID != ''
  env:
    APPLE_ID: ${{ secrets.APPLE_ID }}
    APPLE_APP_SPECIFIC_PASSWORD: ${{ secrets.APPLE_APP_SPECIFIC_PASSWORD }}
    APPLE_TEAM_ID: ${{ secrets.APPLE_TEAM_ID }}
  run: |
    xcrun notarytool submit "${{ steps.artifact.outputs.path }}" \
      --apple-id "$APPLE_ID" \
      --password "$APPLE_APP_SPECIFIC_PASSWORD" \
      --team-id "$APPLE_TEAM_ID" \
      --wait
    xcrun stapler staple "${{ steps.artifact.outputs.path }}"
```

La notarización toma ~5–15 minutos y requiere que `--wait` bloquee la CI; alternativamente `--no-wait` y staple en un paso posterior si notarización es lenta.

---

## Linux

Los `.deb` unsigned funcionan sin problema en sistemas de usuarios finales — `dpkg -i` no requiere firma. Los warnings sólo aparecen si intentas subirlos a repos oficiales de Debian/Ubuntu/PPA, que no es nuestro caso.

Si en el futuro quisieras distribuir vía PPA, lo mínimo es firmar con una llave GPG:

```yaml
- name: Sign .deb (optional, for PPA distribution)
  if: matrix.os == 'ubuntu-latest' && env.GPG_KEY != ''
  env:
    GPG_KEY: ${{ secrets.LINUX_GPG_PRIVATE_KEY_BASE64 }}
    GPG_PASS: ${{ secrets.LINUX_GPG_PASSWORD }}
  run: |
    echo "$GPG_KEY" | base64 -d | gpg --batch --import
    dpkg-sig --sign builder "${{ steps.artifact.outputs.path }}"
```

Por ahora NO lo añadimos al workflow — el `.deb` funciona sin firma para usuarios finales y PPA no está en el roadmap.

---

## Roadmap recomendado

1. **Ship sin firma** (estado actual) y ver qué tracción hay en Windows/Mac
2. Si >10 descargas/semana en Windows → comprar OV cert ($200/año) + añadir paso signtool
3. Si se confirma demanda en Mac → Apple Developer Program ($99/año) + configurar notarización
4. Linux: no firmar salvo que quieras subir a PPA oficiales

Este archivo queda como referencia para cuando llegue el momento. Los steps descritos NO están actualmente en `desktop-release.yml` — son scaffolding que hay que copiar-pegar cuando se compre el certificado correspondiente.
