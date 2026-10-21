import Flutter
import UIKit
import Security
import Vision

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    if let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "CareFlowDeviceKey") {
      DeviceKeyBridge.register(with: registrar)
    }
    if let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "CareFlowDocumentReader") {
      DocumentReaderBridge.register(with: registrar)
    }
  }
}

/// The application can export only a public key and signatures, never private key bytes.
final class DeviceKeyBridge {
  static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "careflow/device-key", binaryMessenger: registrar.messenger())
    channel.setMethodCallHandler { call, result in
      do {
        guard let args = call.arguments as? [String: Any],
              let namespace = args["namespace"] as? String,
              namespace.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil else {
          throw NSError(domain: "DeviceKey", code: 1)
        }
        let key = try privateKey(namespace)
        if call.method == "publicKey" {
          guard let publicKey = SecKeyCopyPublicKey(key) else { throw NSError(domain: "DeviceKey", code: 2) }
          var error: Unmanaged<CFError>?
          guard let data = SecKeyCopyExternalRepresentation(publicKey, &error) as Data?, data.count == 65, data[0] == 4 else {
            throw NSError(domain: "DeviceKey", code: 3)
          }
          result(["jwk": ["kty": "EC", "crv": "P-256", "x": base64url(data.subdata(in: 1..<33)), "y": base64url(data.subdata(in: 33..<65))], "protection": "secure_enclave"])
        } else if call.method == "sign" {
          guard let message = args["message"] as? FlutterStandardTypedData, message.data.count <= 8192 else {
            throw NSError(domain: "DeviceKey", code: 4)
          }
          var error: Unmanaged<CFError>?
          guard let signature = SecKeyCreateSignature(key, .ecdsaSignatureMessageX962SHA256, message.data as CFData, &error) as Data? else {
            throw NSError(domain: "DeviceKey", code: 5)
          }
          result(FlutterStandardTypedData(bytes: signature))
        } else { result(FlutterMethodNotImplemented) }
      } catch {
        result(FlutterError(code: "DEVICE_KEY_UNAVAILABLE", message: "Không thể sử dụng khóa bảo mật thiết bị. Hãy mở khóa iPhone và thử lại.", details: nil))
      }
    }
  }
  private static func privateKey(_ namespace: String) throws -> SecKey {
    let tag = Data(("vn.careflow.dpop." + namespace).utf8)
    let query: [String: Any] = [kSecClass as String: kSecClassKey, kSecAttrApplicationTag as String: tag,
                               kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
                               kSecAttrKeyClass as String: kSecAttrKeyClassPrivate, kSecReturnRef as String: true]
    var value: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &value)
    if status == errSecSuccess, let value = value { return (value as! SecKey) }
    guard status == errSecItemNotFound else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(status)) }
    var error: Unmanaged<CFError>?
    guard let access = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenUnlockedThisDeviceOnly, [.privateKeyUsage], &error) else {
      throw NSError(domain: "DeviceKey", code: 6)
    }
    let attributes: [String: Any] = [kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
      kSecAttrKeySizeInBits as String: 256, kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
      kSecPrivateKeyAttrs as String: [kSecAttrIsPermanent as String: true, kSecAttrApplicationTag as String: tag,
                                     kSecAttrAccessControl as String: access]]
    guard let key = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else { throw NSError(domain: "DeviceKey", code: 7) }
    return key
  }
  private static func base64url(_ data: Data) -> String {
    data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
  }
}

/// Apple's Vision processes image bytes on device. No URLSession, upload or image persistence.
final class DocumentReaderBridge {
  static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "careflow/document-reader", binaryMessenger: registrar.messenger())
    channel.setMethodCallHandler { call, result in
      guard call.method == "read" else { result(FlutterMethodNotImplemented); return }
      guard let args = call.arguments as? [String: Any],
            let data = args["bytes"] as? FlutterStandardTypedData,
            data.data.count <= 5 * 1024 * 1024 else {
        result(FlutterError(code: "DOCUMENT_READ_FAILED", message: "Không thể đọc ảnh giấy tờ trên máy.", details: nil)); return
      }
      DispatchQueue.global(qos: .userInitiated).async {
        do {
          let text = VNRecognizeTextRequest()
          text.recognitionLevel = .accurate
          text.usesLanguageCorrection = false
          let supported = try text.supportedRecognitionLanguages()
          text.recognitionLanguages = ["vi-VN", "en-US"].filter { supported.contains($0) }
          let qr = VNDetectBarcodesRequest()
          qr.symbologies = [.qr]
          // Image-data initializer handles EXIF orientation without writing another file.
          try VNImageRequestHandler(data: data.data, options: [:]).perform([text, qr])
          let lines = (text.results ?? []).compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
          let code = qr.results?.compactMap { $0.payloadStringValue }.first ?? ""
          DispatchQueue.main.async { result(["text": lines, "qr": code]) }
        } catch {
          DispatchQueue.main.async {
            result(FlutterError(code: "DOCUMENT_READ_FAILED", message: "Không thể đọc ảnh giấy tờ trên máy.", details: nil))
          }
        }
      }
    }
  }
}
