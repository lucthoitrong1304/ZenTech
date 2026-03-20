package hcmute.edu.zentech.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendResetPasswordEmail(String toEmail, String link) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, "HCMUTE Gravastar Support");
            helper.setTo(toEmail);
            helper.setSubject("Yêu cầu đặt lại mật khẩu - HCMUTE Gravastar");

            // --- GIAO DIỆN EMAIL ĐỒNG BỘ (THEME XANH LÁ) ---
            String htmlContent = String.format("""
                    <div style="font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; background-color: #f3f4f6; padding: 40px 20px;">
                      <div style="max-width: 500px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 16px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);">
                    
                        <div style="text-align: center; margin-bottom: 30px;">
                          <h1 style="color: #1f2937; margin: 0; font-size: 24px; font-weight: 800; letter-spacing: -0.5px;">UNI MENTOR</h1>
                          <p style="color: #6b7280; font-size: 14px; margin-top: 5px;">Hệ thống hỗ trợ sinh viên</p>
                        </div>
                    
                        <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 20px 0;" />
                    
                        <h2 style="color: #111827; font-size: 20px; font-weight: 600; margin-bottom: 16px;">Yêu cầu đặt lại mật khẩu</h2>
                    
                        <p style="margin-bottom: 24px; font-size: 15px; color: #4b5563;">
                          Xin chào, chúng tôi vừa nhận được yêu cầu khôi phục mật khẩu cho tài khoản của bạn.
                          Để tiếp tục, vui lòng nhấn vào nút bên dưới:
                        </p>
                    
                        <div style="text-align: center; margin-bottom: 32px;">
                          <a href="%s" style="background-color: #16a34a; color: #ffffff; padding: 12px 32px; text-decoration: none; border-radius: 8px; font-weight: 600; display: inline-block; font-size: 15px; box-shadow: 0 4px 6px -1px rgba(22, 163, 74, 0.4);">
                            Đặt lại mật khẩu
                          </a>
                        </div>
                    
                        <p style="margin-bottom: 10px; font-size: 14px; color: #6b7280; background-color: #f9fafb; padding: 12px; border-radius: 6px; border-left: 4px solid #16a34a;">
                          ⚠️ Link này sẽ hết hạn sau <strong>10 phút</strong>. Nếu bạn không yêu cầu, hãy bỏ qua email này.
                        </p>
                    
                        <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 30px 0;" />
                    
                        <div style="text-align: center; font-size: 12px; color: #9ca3af;">
                          <p style="margin: 4px 0;">&copy; 2025 Uni Mentor. All rights reserved.</p>
                          <p style="margin: 4px 0;">HCMUTE - Ho Chi Minh City, Vietnam.</p>
                        </div>
                      </div>
                    </div>
                    """, link);
            // --------------------------------

            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            log.info("Email khôi phục mật khẩu đã được gửi thành công tới: {}", toEmail);

        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("SỰ CỐ GỬI MAIL: Không thể gửi mail tới {}. Chi tiết lỗi: {}", toEmail, e.getMessage());
        }
    }
}