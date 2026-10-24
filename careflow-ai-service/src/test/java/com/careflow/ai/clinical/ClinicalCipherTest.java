package com.careflow.ai.clinical;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;import java.util.*;
class ClinicalCipherTest {
 @Test void randomizedAuthenticatedEncryptionBindsTheActorAndVisit(){var c=new ClinicalCipher(Base64.getEncoder().encodeToString(new byte[32]));String a=c.encrypt("synthetic question and response","actor:visit"),b=c.encrypt("synthetic question and response","actor:visit");assertThat(a).isNotEqualTo(b).doesNotContain("synthetic");assertThat(c.decrypt(a,"actor:visit")).isEqualTo("synthetic question and response");assertThatThrownBy(()->c.decrypt(a,"other:visit")).isInstanceOf(IllegalStateException.class);}
 @Test void weakOrMalformedKeysFailClosed(){assertThatThrownBy(()->new ClinicalCipher("bad")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new ClinicalCipher(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalArgumentException.class);}
}
