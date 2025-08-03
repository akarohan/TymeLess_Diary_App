# 📖 TymeLess - Your Personal Digital Diary

A beautiful, secure, and feature-rich diary application for Android that goes beyond simple note-taking to become your personal history of emotional and mental evolution.

## ✨ Features

### 🖋️ Rich Diary & Notes Experience
- **Rich Text Editor** with bold, italic, underline, strikethrough formatting
- **Smart Notes System** with 3 types: General, Accounts, and Private
- **Mood Tracking** for every entry (0-5 scale)
- **Media Integration** - Add photos and record voice notes
- **Beautiful Themes** with 12+ stunning backgrounds

### 🌙 Personalization & Themes
- **Day/Night Mode** with smooth transitions
- **Theme Collection**: Mountains, Forest, Ocean, Sunset, and more!
- **Custom Backgrounds** for login pages
- **Profile Pictures** and personalized settings

### 🔒 Privacy & Security First
- **Encrypted Storage** using Android Security
- **Password Protection** for sensitive notes
- **Local Offline Backup** - Your data stays private
- **No Internet Required** - Works completely offline

### 💾 Smart Backup & Restore
- **Complete Data Backup** to external storage
- **Selective Backup** - Choose what to backup
- **Easy Restore** from backup files
- **Recycle Bin** for safe deletion

### ⚡ Modern Features
- **Quick Creation** - One-tap new entries
- **Smart Search** through all content
- **Date-based Organization**
- **Smooth Animations** and transitions
- **Responsive Design** for all screen sizes

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 21+ (Android 5.0+)
- Minimum Android version: 5.0 (API 21)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/tymeless-diary.git
   cd tymeless-diary
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned directory and select it

3. **Sync and Build**
   - Wait for Gradle sync to complete
   - Build the project (Build > Make Project)
   - Run on your device or emulator

### Building the APK

1. **Generate Signed APK**
   - Go to Build > Generate Signed Bundle/APK
   - Choose APK
   - Create a new keystore or use existing one
   - Select release build variant
   - Build the APK

2. **Find the APK**
   - Navigate to `app/build/outputs/apk/release/`
   - The APK file will be named `app-release.apk`

## 📱 Screenshots

[Add screenshots of your app here]

## 🛠️ Technical Details

### Architecture
- **MVVM Architecture** with ViewModel and LiveData
- **Room Database** for local data storage
- **Encrypted SharedPreferences** for secure settings
- **Material Design** components
- **Navigation Component** for screen navigation

### Key Libraries Used
- **Room**: Local database
- **LiveData & ViewModel**: Architecture components
- **EncryptedSharedPreferences**: Secure storage
- **Glide**: Image loading
- **Material Design**: UI components
- **Navigation Component**: Screen navigation

### Project Structure
```
app/
├── src/main/
│   ├── java/com/example/diaryapp/
│   │   ├── ui/home/          # Home screen and diary entries
│   │   ├── ui/notes/         # Notes functionality
│   │   ├── data/             # Database and data models
│   │   └── utils/            # Utility classes
│   ├── res/
│   │   ├── layout/           # XML layouts
│   │   ├── drawable/         # Images and drawables
│   │   ├── values/           # Colors, strings, themes
│   │   └── navigation/       # Navigation graphs
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## 🔧 Configuration

### Permissions Required
- **Storage**: For backup and restore functionality
- **Camera**: For taking photos
- **Microphone**: For voice recording
- **Internet**: Optional, for future features

### Build Configuration
The app uses the following build configuration:
- **Minimum SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

## 🤝 Contributing

We welcome contributions! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

### How to Contribute
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Material Design** for the beautiful UI components
- **Android Jetpack** for the architecture components
- **Room Database** for efficient local storage
- **Glide** for smooth image loading

## 📞 Support

If you encounter any issues or have questions:
- Open an issue on GitHub
- Check the existing issues for solutions
- Contact the maintainers

## 🔮 Future Plans

- [ ] Cloud sync functionality
- [ ] Web companion app
- [ ] Advanced search and filters
- [ ] Export to various formats
- [ ] Collaborative features
- [ ] AI-powered insights

---

**Made with ❤️ for privacy-conscious users who value their personal digital space.**

*TymeLess - Where your thoughts find their timeless home.* 