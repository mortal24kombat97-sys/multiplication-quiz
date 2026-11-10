# تحدي جدول الضرب — النسخة الموسعة أونلاين

## المميزات المضافة
- ملف شخصي باسم اللاعب وصورة يرفعها إلى Firebase Storage.
- إنشاء غرف خاصة برمز من 6 أحرف/أرقام والانضمام إليها.
- قائمة لاعبين ونتائج مباشرة داخل الغرفة.
- 20 سؤالاً عشوائياً دون تكرار العملية داخل الجولة.
- حفظ مجموع النقاط وعدد المباريات والانتصارات في ملف اللاعب.
- لوحة متصدرين عامة تعرض أفضل 50 لاعباً.
- تبقى بيانات الملف الشخصي والإحصائيات محفوظة بعد إغلاق التطبيق.

## إعداد Firebase (إلزامي)
1. افتح https://console.firebase.google.com وأنشئ مشروع Firebase.
2. أضف تطبيق Android بمعرّف الحزمة:
   `com.example.multiplicationquiz`
3. نزّل `google-services.json` وضعه داخل مجلد `app/`.
4. في Authentication > Sign-in method فعّل **Anonymous**.
5. أنشئ Realtime Database.
6. انسخ Database URL وضعه داخل `MainActivity.kt` باستبدال:
   `FirebaseDatabase.getInstance()`
   بـ:
   `FirebaseDatabase.getInstance("رابط قاعدة البيانات")`
7. فعّل Firebase Storage.
8. في Storage > Rules، استخدم قواعد اختبار مؤقتة فقط أثناء التطوير، ولا تنشرها للعامة.

## قواعد Realtime Database — تجربة أولية فقط
```json
{
  "rules": {
    "rooms": {
      "$room": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "profiles": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null && auth.uid == $uid"
      }
    }
  }
}
```

## قواعد Storage — تجربة أولية فقط
```text
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /avatars/{userId}/{allPaths=**} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId
                   && request.resource.size < 3 * 1024 * 1024
                   && request.resource.contentType.matches('image/.*');
    }
  }
}
```

**تحذير أمني مهم:** القواعد أعلاه للتجربة الأولية وليست مناسبة لإطلاق عام. في النسخة الحالية، التطبيق يرسل الإجابات ويحدّث النقاط من جهة العميل؛ لذلك يمكن لمستخدم خبيث التلاعب بالنتائج أو بيانات الغرف. قبل نشر التطبيق للعامة، انقل بدء/إنهاء الجولات والتحقق من الإجابات واحتساب النقاط إلى خادم موثوق (مثل Cloud Functions)، وقيّد صلاحيات كتابة الغرف، وأضف حماية من السبام وانتهاء صلاحية الغرف. كذلك تخزين النقاط الإجمالية في ملف اللاعب يجب أن يكون خادميّاً لضمان النزاهة.

## بناء APK
1. افتح المشروع في Android Studio.
2. ضع `google-services.json` داخل `app/`.
3. انتظر Gradle Sync.
4. اختر Build > Build Bundle(s) / APK(s) > Build APK(s).
5. ستجد APK التجريبي غالباً في:
   `app/build/outputs/apk/debug/app-debug.apk`

يتطلب تنزيل اعتمادات Gradle وFirebase اتصال إنترنت أول مرة. اللعب الجماعي ولوحة المتصدرين يتطلبان اتصالاً بالإنترنت دائماً.
