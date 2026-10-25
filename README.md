# Nimbus

<div dir="rtl">

برنامه‌ی اندرویدی ساده برای اتصال به اینترنت آزاد، با هسته‌ی متن‌باز [Aether](https://github.com/CluvexStudio/Aether).

- اتصال با یک لمس؛ به‌صورت **VPN برای کل گوشی** یا **پراکسی** (SOCKS5 و HTTP) برای برنامه‌های دیگر
- شش مسیر: MASQUE، WireGuard، Gool، MASQUE دولایه، سایفون و Tor
- نمایش سرعت، حجم مصرفی، کشور و آی‌پی خروجی و تأخیر
- انتخاب برنامه‌هایی که از VPN رد شوند یا نشوند
- کاشی تنظیمات سریع (Quick Settings) و پشتیبانی از «VPN همیشه روشن» اندروید
- فارسی و انگلیسی، پوسته‌ی روشن و تیره
- بررسی خودکار نسخه‌ی جدید و نصب با یک دکمه

## نصب

از بخش [Releases](../../releases/latest) یکی از فایل‌ها را دانلود و نصب کنید:

- بیشتر گوشی‌ها: فایلی که آخرش `arm64-v8a.apk` است
- گوشی‌های قدیمی: `armeabi-v7a.apk`
- اگر مطمئن نیستید: `universal.apk` (حجیم‌تر، روی همه کار می‌کند)

برنامه به اندروید ۱۰ یا جدیدتر نیاز دارد.

</div>

## English

Nimbus is a simple Android client for the [Aether](https://github.com/CluvexStudio/Aether) censorship-circumvention core. It runs the core as a full-device VPN (through [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)) or as a local SOCKS5/HTTP proxy, with MASQUE, WireGuard, Gool, MASQUE-in-MASQUE, Psiphon and Tor routes. Android 10 or later.

### How it is built

Every push builds the app on GitHub Actions: the official Aether Android binaries of the latest release are downloaded and checked against the release's `SHA256SUMS.txt`, hev-socks5-tunnel is built from a pinned commit with the NDK, unit tests run, every command line the app can produce is run against the real core, and the APK is installed on an emulator that connects for real and sends traffic through the VPN and the proxy before a release is published. A daily job rebuilds when Aether publishes a new core.

### Licence

Nimbus is free software under the [GNU AGPL 3.0](LICENSE). It bundles Aether (AGPL-3.0), hev-socks5-tunnel (MIT), psiphon-tunnel-core (GPL-3.0) and lyrebird (BSD-3 / GPL-3.0); their licence texts ship with each release. "Aether" is a trademark of CluvexStudio; Nimbus is not made by or affiliated with the Aether project. The Vazirmatn font is under the SIL Open Font License ([tools/Vazirmatn-OFL.txt](tools/Vazirmatn-OFL.txt)).
