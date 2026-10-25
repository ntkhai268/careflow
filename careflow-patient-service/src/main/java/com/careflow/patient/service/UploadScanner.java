package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Private clamd INSTREAM transport; upload bytes never leave the configured scanning service. */
@Component
public class UploadScanner {
    private final String host;
    private final int port;
    private final boolean required;
    public UploadScanner(@Value("${security.file.scanner-host:}") String host,
                         @Value("${security.file.scanner-port:3310}") int port,
                         @Value("${security.file.scan-required:false}") boolean required) {
        this.host=host;this.port=port;this.required=required;
    }
    public void scan(byte[] bytes) {
        if(host.isBlank()) {
            if(required) throw unavailable();
            return;
        }
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress(host,port),3000);socket.setSoTimeout(10000);
            var output=new DataOutputStream(socket.getOutputStream());output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for(int offset=0;offset<bytes.length;offset+=8192) {
                int size=Math.min(8192,bytes.length-offset);output.writeInt(size);output.write(bytes,offset,size);
            }
            output.writeInt(0);output.flush();
            var input=socket.getInputStream();var result=new ByteArrayOutputStream();
            int value;while((value=input.read())>0 && result.size()<1024) result.write(value);
            String reply=result.toString(StandardCharsets.US_ASCII);
            if(reply.contains(" FOUND")) throw new BusinessException(422,"Tệp không vượt qua kiểm tra an toàn.");
            if(!"stream: OK".equals(reply.strip())) throw unavailable();
        } catch(BusinessException failure) { throw failure; }
        catch(IOException failure) { throw unavailable(); }
    }
    private static BusinessException unavailable() { return new BusinessException(503,"Chưa thể kiểm tra an toàn tệp. Vui lòng thử lại sau."); }
}
