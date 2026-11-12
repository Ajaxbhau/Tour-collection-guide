TOUR COLLECTION MANAGER — ANDROID PROJECT

यह native Android app project है (HTML/WebView नहीं)।
Features: offline SQLite storage, Class 9 Sections A/B/C, add/edit/delete entries,
section-wise totals, grand total, PDF report और Excel-compatible CSV export.

PC नहीं है? फोन से APK बनाने का आसान तरीका (GitHub Actions):
1. ZIP को फोन में डाउनलोड करके Extract करें।
2. github.com पर अपना account बनाएं / login करें।
3. New repository बनाएं, नाम TourCollectionManager रखें और Public चुनें।
4. फोन के browser में repository खोलें। Add file > Upload files चुनें।
5. Extract किए हुए TourCollectionManager folder के अंदर की सभी files और folders upload करें।
   ध्यान दें: .github folder भी upload होना चाहिए। फिर Commit changes दबाएं।
6. Repository में Actions tab खोलें। Build Android APK workflow चुनें और Run workflow दबाएं
   (अगर push पर build अपने-आप शुरू हो चुका है, तो उसके पूरा होने का इंतजार करें)।
7. हरे रंग का successful run खोलें। नीचे Artifacts में
   Tour-Collection-Manager-APK.zip डाउनलोड करें।
8. उस ZIP को फोन में extract करें। app-debug.apk पर tap करके install करें।
   Android पूछे तो Chrome/Files को इस source से app install करने की अनुमति दें।

ज़रूरी बातें:
- GitHub account और इंटरनेट सिर्फ APK build करने के लिए चाहिए; installed app offline चलेगा।
- GitHub repository Public रखने पर source code सार्वजनिक होगा; इसमें कोई student data न डालें।
- इस project में student data नहीं है, केवल app source code है।
- Export CSV है, जिसे Excel/Google Sheets खोल सकते हैं; native .xlsx नहीं।
- App uninstall या app data clear करने पर local database मिट सकता है; CSV export/backup रखें।
