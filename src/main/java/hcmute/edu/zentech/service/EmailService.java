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

            helper.setFrom(fromEmail, "ZenTech Support");
            helper.setTo(toEmail);
            helper.setSubject("Yêu cầu đặt lại mật khẩu - ZenTech");

            // --- GIAO DIỆN EMAIL ĐỒNG BỘ (THEME LIGHT ZENTECH) ---
            String htmlContent = String.format("""
                    <div style="font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #374151; background-color: #f8fafc; padding: 50px 20px; min-height: 100%%;">
                      <div style="max-width: 550px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 16px; border: 1px solid #e2e8f0; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05);">
                        
                        <!-- Header / Logo -->
                        <div style="text-align: center; margin-bottom: 30px;">
                          <div style="display: inline-block; padding: 6px 12px; background-color: #0a0a0a; border-radius: 8px; margin-bottom: 12px;">
                            <span style="color: #ffffff; font-weight: 900; font-size: 20px; letter-spacing: 1px;">ZT</span>
                          </div>
                          <h1 style="color: #0a0a0a; margin: 0; font-size: 26px; font-weight: 900; letter-spacing: 2px; text-transform: uppercase;">
                            <span style="color: #0a0a0a;">ZEN</span><span style="color: #ffb300;">TECH</span>
                          </h1>
                          <p style="color: #6b7280; font-size: 13px; margin: 6px 0 0 0; font-weight: 500; letter-spacing: 0.5px;">Hệ thống bán lẻ thiết bị công nghệ cao cấp</p>
                        </div>
                    
                        <div style="border-top: 1px solid #e2e8f0; margin: 25px 0;"></div>
                    
                        <!-- Content -->
                        <h2 style="color: #0a0a0a; font-size: 20px; font-weight: 800; margin-bottom: 20px; letter-spacing: -0.3px; text-align: center;">Yêu Cầu Đặt Lại Mật Khẩu</h2>
                    
                        <p style="margin-bottom: 24px; font-size: 15px; color: #4b5563; text-align: center; font-weight: 400; line-height: 1.7;">
                          Xin chào,<br>
                          Chúng tôi nhận được yêu cầu khôi phục mật khẩu cho tài khoản của bạn tại hệ thống <strong>ZenTech</strong>.<br>
                          Vui lòng nhấn vào nút bên dưới để tiến hành thiết lập mật khẩu mới:
                        </p>
                    
                        <!-- Call to Action Button -->
                        <div style="text-align: center; margin-bottom: 35px;">
                          <a href="%s" style="background-color: #ffc700; color: #0a0a0a; padding: 14px 36px; text-decoration: none; border-radius: 999px; font-weight: 800; display: inline-block; font-size: 14px; text-transform: uppercase; letter-spacing: 1px; box-shadow: 0 4px 15px rgba(255, 199, 0, 0.2);">
                            Đặt lại mật khẩu
                          </a>
                        </div>
                    
                        <!-- Expiry Alert Box -->
                        <div style="margin-bottom: 10px; font-size: 13px; color: #374151; background-color: #fffbeb; padding: 16px; border-radius: 10px; border-left: 4px solid #ffb300; text-align: left; line-height: 1.6;">
                          <span style="font-size: 16px; margin-right: 6px; vertical-align: middle;">⚠️</span>
                          Liên kết này sẽ hết hiệu lực sau <strong>10 phút</strong>. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này một cách an toàn.
                        </div>
                    
                        <div style="border-top: 1px solid #e2e8f0; margin: 30px 0;"></div>
                    
                        <!-- Footer -->
                        <div style="text-align: center; font-size: 12px; color: #6b7280; line-height: 1.8;">
                          <p style="margin: 0 0 8px 0;">Bạn có thắc mắc? Liên hệ hỗ trợ tại <a href="mailto:service@zentech.com" style="color: #b45309; text-decoration: none; font-weight: 600;">service@zentech.com</a></p>
                          <p style="margin: 0 0 4px 0; font-weight: 500;">&copy; 2026 ZenTech Systems Inc. All rights reserved.</p>
                          <p style="margin: 0;">HCMUTE - Ho Chi Minh City, Vietnam.</p>
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