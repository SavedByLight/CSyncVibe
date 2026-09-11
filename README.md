# CSyncVibe

**CSyncVibe** is a modern Android app written in Kotlin that securely syncs your device contacts to a GitHub repository.

## Features

- 📱 Read all contacts (name, phone numbers, emails)
- 🔒 Secure storage of GitHub Personal Access Token using EncryptedSharedPreferences
- ⬇️ **Download & Import** contacts from GitHub (safe for a second device)
- ⬆️ **Upload / Overwrite** only when you explicitly confirm
- 🛠️ Clean Material 3 UI
- ✅ GitHub Actions CI that builds debug + signed release APKs and publishes GitHub Releases on tags

## How it works

1. Provide a GitHub **Personal Access Token** (classic) with the `repo` scope.
2. Specify the repository owner, name, and target file path (default: `contacts/contacts.json`).
3. **Download & Import** pulls the JSON from GitHub and inserts only new contacts.
4. **Upload / Overwrite** (with confirmation) replaces the file on GitHub with contacts from this device.

## Setup (app usage)

1. Create a classic GitHub PAT with the **`repo`** scope.
2. Open the app → paste token, owner, repo → Save Settings.
3. Grant Contacts permissions.
4. On the device that has the contacts → **Upload / Overwrite**.
5. On other devices → **Download & Import**.

## Signing & GitHub Releases (CI)

### 1. Create a release keystore (one-time, on your machine)

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias csyncvibe \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Keep the keystore and passwords safe. If you lose them you cannot update the app with the same signing key.

### 2. Encode the keystore for GitHub Secrets

```bash
base64 -w 0 release.keystore > keystore.base64.txt
# macOS: base64 -i release.keystore -o keystore.base64.txt
```

### 3. Add repository secrets

Go to your repo → **Settings → Secrets and variables → Actions** and create:

| Secret name         | Value                                      |
|---------------------|--------------------------------------------|
| `KEYSTORE_BASE64`   | Contents of `keystore.base64.txt`          |
| `KEYSTORE_PASSWORD` | Keystore password                          |
| `KEY_ALIAS`         | `csyncvibe` (or the alias you chose)       |
| `KEY_PASSWORD`      | Key password                               |

### 4. Trigger a signed release

Push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow will:

1. Build the debug APK  
2. Decode the keystore from secrets  
3. Build a **signed** release APK  
4. Create a **GitHub Release** for the tag and attach both APKs  

You can also run the workflow manually from the **Actions** tab (`workflow_dispatch`).

### Local signed builds (optional)

```bash
cp keystore.properties.example keystore.properties
# edit keystore.properties with your paths/passwords
./gradlew assembleRelease
```

`keystore.properties` and `*.keystore` are gitignored.

## Output format

```json
{
  "synced_at": "2026-09-11T07:12:00Z",
  "contact_count": 42,
  "contacts": [
    {
      "id": "123",
      "display_name": "Alice Example",
      "phones": ["+1 555-0100"],
      "emails": ["alice@example.com"]
    }
  ]
}
```

## Permissions

- `READ_CONTACTS` – read contacts for upload  
- `WRITE_CONTACTS` – import contacts from GitHub  
- `INTERNET` – GitHub API  

## Security notes

- The GitHub token is stored with AndroidX Security Crypto (AES-256).
- Never commit `release.keystore`, `keystore.properties`, or passwords.
- Prefer a private repo for the contacts JSON.

## License

MIT
