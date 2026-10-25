package com.careflow.identity.document;
import com.careflow.common.exception.BusinessException;
import org.springframework.web.bind.annotation.*;

/** Old apps must never upload identity photos to an external provider. */
@RestController
public class RetiredEkycController {
    @RequestMapping({"/api/auth/ekyc/status","/api/auth/ekyc/verify"})
    public void retired() { throw new BusinessException(410,"Đã ngừng eKYC bên ngoài. Cập nhật app để đọc giấy tờ trên máy và đối chiếu trực tiếp."); }
}
