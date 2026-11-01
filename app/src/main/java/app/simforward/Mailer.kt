package app.simforward

import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** 極簡 SMTP 客戶端：465 隱式 SSL 或 587 STARTTLS，AUTH LOGIN，UTF-8 純文字郵件。 */
object Mailer {

    private const val TIMEOUT = 8000

    fun send(
        host: String, port: Int, user: String, pass: String,
        to: String, subject: String, body: String
    ) {
        val cleanTo = to.replace("\r", "").replace("\n", "").trim()
        val from = user.replace("\r", "").replace("\n", "").trim().let {
            if (it.contains("@")) it else cleanTo
        }
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory

        var socket: Socket
        if (port == 465) {
            socket = connectTls(factory, null, host, port)
        } else {
            socket = Socket()
            socket.soTimeout = TIMEOUT
            socket.connect(InetSocketAddress(host, port), TIMEOUT)
        }

        try {
            var reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            var out = socket.getOutputStream()

            expect(reader, 220)
            cmd(out, "EHLO simforward.local"); expect(reader, 250)

            if (port != 465) {
                cmd(out, "STARTTLS"); expect(reader, 220)
                socket = connectTls(factory, socket, host, port)
                reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                out = socket.getOutputStream()
                cmd(out, "EHLO simforward.local"); expect(reader, 250)
            }

            cmd(out, "AUTH LOGIN"); expect(reader, 334)
            cmd(out, b64(user)); expect(reader, 334)
            cmd(out, b64(pass)); expect(reader, 235)
            cmd(out, "MAIL FROM:<$from>"); expect(reader, 250)
            cmd(out, "RCPT TO:<$cleanTo>"); expect(reader, 250, 251)
            cmd(out, "DATA"); expect(reader, 354)

            val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date())
            val bodyB64 = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
                .replace("\r", "").replace("\n", "\r\n").trimEnd()
            val msg = buildString {
                append("From: <$from>\r\n")
                append("To: <$cleanTo>\r\n")
                append("Subject: =?UTF-8?B?${b64(subject)}?=\r\n")
                append("Date: $date\r\n")
                append("Message-ID: <${UUID.randomUUID()}@simforward.local>\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: base64\r\n")
                append("\r\n")
                append(bodyB64)
                append("\r\n.\r\n")
            }
            out.write(msg.toByteArray(Charsets.US_ASCII))
            out.flush()
            expect(reader, 250)
            cmd(out, "QUIT")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun connectTls(f: SSLSocketFactory, base: Socket?, host: String, port: Int): Socket {
        val s = (if (base == null) {
            val raw = Socket()
            raw.connect(InetSocketAddress(host, port), TIMEOUT)
            f.createSocket(raw, host, port, true)
        } else {
            f.createSocket(base, host, port, true)
        }) as SSLSocket
        s.soTimeout = TIMEOUT
        s.startHandshake()
        // 必須自己驗證主機名，否則可被中間人攔截
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, s.session)) {
            s.close()
            throw javax.net.ssl.SSLPeerUnverifiedException("憑證主機名不符：$host")
        }
        return s
    }

    private fun b64(s: String) =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun cmd(out: OutputStream, line: String) {
        out.write((line + "\r\n").toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** 讀取（可能多行的）回應，檢查狀態碼 */
    private fun expect(r: BufferedReader, vararg ok: Int) {
        var line: String
        var code: Int
        while (true) {
            line = r.readLine() ?: throw java.io.IOException("伺服器中斷連線")
            code = line.take(3).toIntOrNull() ?: throw java.io.IOException("異常回應：$line")
            if (line.length < 4 || line[3] != '-') break
        }
        if (code !in ok) throw java.io.IOException("SMTP 錯誤：$line")
    }
}
