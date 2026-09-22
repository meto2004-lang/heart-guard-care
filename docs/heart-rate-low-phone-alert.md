# إنذار الجوال عند انخفاض النبض إلى 70

## السلوك

- العتبة شاملة: **70 نبضة/دقيقة أو أقل**.
- عند عبور الحد يصدر الجوال صفارة إنذار عالية (نفس مسار SOS) ويرسل SMS ثم مكالمة لجهات الاتصال الطارئة ذات الخطورة الحرجة.
- الإنذار **مرة واحدة لكل نوبة**. لا يتكرر طالما النبض بقي عند 70 أو أقل. يُعاد التسليح فقط بعد عودة النبض فوق 70 ثم الهبوط مجدداً.
- القيمة 0 أو القراءات غير الصالحة لا تُشغّل الإنذار ولا تُعيد التسليح.

هذه عتبة طلبها المستخدم/مقدّم الرعاية، وليست تعريفاً طبياً لبطء القلب.

## أين يُكشف الحدث

1. **الساعة:** `HeartRateAnomalyDetector` يطلق `LOW` أو `CRITICAL_LOW` مرة واحدة لكل نوبة، ثم `EmergencyAlertService` يرسل تنبيهاً من نوع `HEART_RATE_LOW` / `HEART_RATE_CRITICAL_LOW` بخطورة `CRITICAL`.
2. **الجوال:** `HeartRateLowAlertCoordinator` يراقب بيانات النبض الحية (Wear أو HTTP). إذا وصلت القراءة دون تنبيه من الساعة، يُنشئ الحدث محلياً.
3. **منع التكرار:** بوابة نوبة مشتركة على الجوال. نسخة الساعة ونسخة الجوال لا تُطلقان صفارة/رسائل مرتين لنفس الهبوط.

## الاختبارات

- `HeartRateLowEpisodeGateTest`
- `HeartRateAlertPolicyTest`
- `HeartRateAnomalyDetectorTest`

```bash
bash ./gradlew :shared:testDebugUnitTest :watch:app:testDebugUnitTest :mobile:app:testDebugUnitTest
```
