package handler;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import model.Account;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import service.AccountService;
import service.commerce.CommerceService;

@RestController
public class TicketQrHandler {
  private final AccountService accounts;
  private final CommerceService commerce;

  public TicketQrHandler(AccountService accounts, CommerceService commerce) {
    this.accounts = accounts;
    this.commerce = commerce;
  }

  @GetMapping(value = "/api/commerce/tickets/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> qr(@PathVariable String id, Authentication authentication)
      throws Exception {
    Account account = accounts.require(authentication);
    Map<String, Object> ticket =
        commerce.ownedTicket(id, account.customerId(), account.role().equals("ADMIN"));
    return image("N6-DEMO:" + ticket.get("token"));
  }

  @GetMapping(value = "/api/commerce/payments/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> paymentQr(@PathVariable String id, Authentication authentication)
      throws Exception {
    Account account = accounts.require(authentication);
    Map<String, Object> payment =
        commerce.payment(account.customerId(), account.role().equals("ADMIN"), id);
    return image("N6-DEMO:PAY:" + payment.get("id") + ":" + payment.get("amount"));
  }

  private ResponseEntity<byte[]> image(String payload) throws Exception {
    var bits =
        new QRCodeWriter()
            .encode(payload, BarcodeFormat.QR_CODE, 280, 280, Map.of(EncodeHintType.MARGIN, 2));
    ByteArrayOutputStream image = new ByteArrayOutputStream();
    MatrixToImageWriter.writeToStream(bits, "PNG", image);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(image.toByteArray());
  }
}
