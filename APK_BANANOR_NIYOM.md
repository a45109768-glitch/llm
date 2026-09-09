# এই প্রজেক্ট থেকে APK কীভাবে বানাবেন (GitHub Actions দিয়ে অটোমেটিক)

আমি প্রজেক্টটা চেক করে দেখেছি — এটা একটা সম্পূর্ণ Android (Kotlin + Compose) প্রজেক্ট,
নাম "PocketAI"। কোডে বড় কোনো সমস্যা নেই, কিন্তু এই প্যাকেজে দুটো জিনিস মিসিং ছিল যেটার
কারণে সরাসরি বিল্ড হতো না:

1. **`gradlew` / `gradlew.bat` / `gradle-wrapper.jar`** ফাইলগুলো এক্সপোর্টে ছিল না
   (AI Studio থেকে zip export করলে অনেক সময় এগুলো বাদ পড়ে যায়)।
2. Release সাইনিং-এর জন্য যে keystore লাগে সেটাও ছিল না (এটা স্বাভাবিক, কারণ এটা
   গোপনীয় ফাইল, কখনো zip/GitHub এ commit করা ঠিক না)।

আমি একটা **GitHub Actions workflow** যুক্ত করে দিয়েছি
(`.github/workflows/build-apk.yml`), যা GitHub-এর সার্ভারে (ইন্টারনেট আছে সেখানে)
প্রতিবার push করলে অটোমেটিক APK বানিয়ে দেবে। কোনো Android Studio বা কম্পিউটারে
বিল্ড করার দরকার নেই।

## যেভাবে ব্যবহার করবেন

1. GitHub-এ একটা নতুন repository বানান (public বা private, যেকোনোটা)।
2. এই পুরো ফোল্ডারটা push করুন:
   ```bash
   cd llm-main
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<আপনার-ইউজারনেম>/<repo-নাম>.git
   git push -u origin main
   ```
3. push হয়ে গেলে GitHub repo-র **"Actions"** ট্যাবে যান — "Build APK" workflow
   নিজে থেকেই শুরু হয়ে যাবে (২-৫ মিনিট লাগতে পারে)।
4. workflow শেষ হলে সেই run-এর পেজে নিচে **"Artifacts"** সেকশনে
   `app-debug-apk` নামে একটা zip পাবেন — সেটা ডাউনলোড করে ভেতরের `.apk` ফাইলটা
   আপনার ফোনে ইনস্টল করতে পারবেন (debug APK, টেস্টিংয়ের জন্য এটাই যথেষ্ট)।

## Play Store-এর মতো সাইন করা (release) APK চাইলে

যদি প্রকৃত সাইন করা release APK লাগে (Play Store-এ আপলোড করার মতো), তাহলে:

1. নিজের একটা keystore (`.jks` ফাইল) বানান, বা আগের থাকলে সেটা ব্যবহার করুন।
2. GitHub repo-র **Settings → Secrets and variables → Actions**-এ গিয়ে এই ৩টা
   secret যুক্ত করুন:
   - `RELEASE_KEYSTORE_BASE64` — আপনার `.jks` ফাইলকে base64 করে তার ভ্যালু
     (কমান্ড: `base64 -w 0 my-upload-key.jks`)
   - `RELEASE_STORE_PASSWORD`
   - `RELEASE_KEY_PASSWORD`
3. এরপর নতুন করে push করলে workflow-টা `app-release-apk` নামেও একটা আর্টিফ্যাক্ট
   বানিয়ে দেবে।

এই secret গুলো না দিলে ওই স্টেপগুলো এমনি স্কিপ হয়ে যাবে, workflow fail হবে না —
শুধু debug APK টাই বানাবে।

## যদি কম্পিউটারেই (Android Studio) বিল্ড করতে চান

`gradlew` ফাইলগুলো এক্সপোর্টে ছিল না বলে সরাসরি টার্মিনাল থেকে `./gradlew` চালানো
যাবে না। Android Studio-তে এই ফোল্ডার **Open** করলে Android Studio নিজেই
wrapper ফাইলগুলো বসিয়ে নেয় / "Sync Project with Gradle Files" করলে ঠিক হয়ে যায়।
তারপর সাধারণভাবে Run/Build করতে পারবেন। আমি একটা `debug.keystore` ফাইলও
যুক্ত করে দিয়েছি (প্রজেক্টের রুটে) যাতে debug বিল্ড লোকালিও সাইন হতে পারে।
