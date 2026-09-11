# CSyncVibe

**CSyncVibe** is a modern Android app written in Kotlin that securely syncs your device contacts to a GitHub repository.

## Features

- 📱 Read all contacts (name, phone numbers, emails)
- 🔒 Secure storage of GitHub Personal Access Token using EncryptedSharedPreferences
- ☁️ Push contacts as pretty-printed JSON to any file path in your repo
- 🔄 One-tap manual sync + automatic background sync every 12 hours
- 🛠️ Clean Material 3 UI
- ✅ GitHub Actions CI that builds the debug APK on every push

## How it works

1. You provide a GitHub **Personal Access Token** (classic) with the `repo` scope.
2. You specify the repository owner, name, and the target file path (default: `contacts/contacts.json`).
3. The app queries `ContactsContract`, builds a clean JSON payload, and uses the GitHub Contents API to create or update the file.

## Setup

### 1. Create a GitHub Personal Access Token

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate a new token with the **`repo`** scope
3. Copy the token (you will only see it once)

### 2. Create / choose a repository

You can use a private repository. The app will create the target file if it does not exist.

### 3. Build & install the app

```bash
git clone <your-fork>
cd CSyncVibe
./gradlew assembleDebug
```

Install the APK from `app/build/outputs/apk/debug/`.

Or open the project in Android Studio and run it.

### 4. Configure inside the app

1. Paste your GitHub token
2. Enter repository owner (your username or organization)
3. Enter repository name
4. (Optional) change the file path
5. Tap **Save Settings**
6. Grant Contacts permission when prompted
7. Tap **Sync Contacts Now**

## Output format

The JSON written to your repo looks like:

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

- `READ_CONTACTS` – required to read contact data
- `INTERNET` – required to talk to GitHub API
- `POST_NOTIFICATIONS` – for future notification support

## Security notes

- The GitHub token is stored using AndroidX Security Crypto (AES-256 encrypted SharedPreferences).
- The token is never logged or written to unencrypted storage.
- Consider using a fine-scoped token and a private repository.

## CI

Every push / PR to `main` or `master` triggers `.github/workflows/build.yml`, which builds the debug APK and uploads it as an artifact.

## License

MIT
