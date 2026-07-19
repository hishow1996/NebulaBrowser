该目录用于放置 yt-dlp 单文件二进制（PyInstaller 冻结版）。

构建说明（在 Linux x86_64 主机上执行）：
1. git clone https://github.com/yt-dlp/yt-dlp
2. python -m pip install pyinstaller
3. 为 Android 各 ABI 交叉编译 python 解释器（推荐用 python-for-android 或 termux 的 python）
4. PyInstaller 打包 yt-dlp __main__.py 为单文件可执行程序
5. 分别放入：
   - ytdlp/bin_arm64 (arm64-v8a)
   - ytdlp/bin_arm (armeabi-v7a)
   - ytdlp/bin_x64 (x86_64)
   - ytdlp/bin_x86 (x86)
6. APK 启动后 NebulaVideoExtractor 会从 assets 复制对应 ABI 的二进制到 filesDir
7. ProcessBuilder 调用 ./bin --dump-json <url>

注：因体积和合规性限制，APK 默认不附带二进制。
应用启动时会从 GitHub Release 热更新下载。
