# Git Workflow — CareFlow Team (3 người)

## 1. Tổng quan nhánh

```
main          ●───────────────●───────────────●──────── (chỉ merge khi ổn định)
               \             ↑               ↑
develop         ●────●────●────●────●────●────●──────── (nhánh tích hợp chung)
                 \       ↑ \       ↑ \       ↑
feature/...       ●──●──●   ●──●──●   ●──●──●  ─────── (mỗi người code ở đây)
```

| Nhánh | Mục đích | Ai được push? |
|-------|----------|---------------|
| `main` | Code ổn định, demo được | Chỉ merge từ `develop` (cả nhóm đồng ý) |
| `develop` | Tích hợp code của cả nhóm | Merge từ feature branches |
| `feature/*` | Mỗi tính năng riêng biệt | Người phụ trách tính năng đó |

---

## 2. Quy tắc đặt tên nhánh

```
feature/<tên-người>/<mô-tả-ngắn>
```

### Ví dụ:

| Người | Ví dụ tên nhánh |
|-------|-----------------|
| **A** (Hệ thống) | `feature/a/identity-service` |
| | `feature/a/queue-algorithm` |
| | `feature/a/notification-websocket` |
| **B** (Bệnh nhân) | `feature/b/patient-crud` |
| | `feature/b/appointment-booking` |
| | `feature/b/mobile-login` |
| **C** (Bác sỹ) | `feature/c/consultation-api` |
| | `feature/c/prescription-service` |
| | `feature/c/web-dashboard` |

> 💡 Nhánh fix bug: `fix/a/queue-miss-turn`
> 💡 Nhánh hotfix: `hotfix/docker-compose-port`

---

## 3. Luồng làm việc hàng ngày

### Bước 1: Cập nhật code mới nhất

```bash
git pull origin develop          # kéo code mới từ develop vào nhánh feature hiện tại
```

### Bước 2: Code → Commit → Push

```bash
# Code xong 1 phần → commit ngay
git add .
git commit -m "feat(identity): add User entity and repository"

# Push lên remote (backup + cho người khác thấy tiến độ)
git push origin feature/a/identity-service
```

### Bước 3: Hoàn thành tính năng → Merge vào develop

```bash
# 1. Merge code mới nhất từ develop vào nhánh feature
#    (git pull = fetch + merge, nên develop được merge vào feature ở bước này)
git pull origin develop

# 2. Nếu có conflict → giải quyết, rồi commit:
git add .
git commit -m "merge: resolve conflict with develop"

# 3. BUILD + TEST — đảm bảo code của mình + code từ develop chạy đúng
#    Ví dụ: mvn clean compile, chạy thử service, test API...
#    ⚠️ Chưa test OK thì CHƯA push!

# 4. Test OK → push
git push origin feature/a/identity-service

# 5. Lên GitHub → tạo Pull Request
#    From: feature/a/identity-service  →  To: develop
#    → Nhờ thành viên khác review → Merge PR → Done ✅
```

> 💡 **Tại sao dùng PR?** Vì conflict được giải quyết trên nhánh feature, develop luôn sạch. Nếu có lỗi thì chỉ nhánh feature bị ảnh hưởng.

> 📌 **Lần đầu chưa có nhánh feature?** Tạo như sau:
> ```bash
> git checkout develop
> git pull origin develop
> git checkout -b feature/a/identity-service    # tạo nhánh mới từ develop
> ```
> Từ ngày hôm sau trở đi chỉ cần làm Bước 1 → 2 → 3.

---

## 4. Quy tắc viết commit message

### Format

```
<loại>(<phạm-vi>): <mô tả ngắn>
```

### Các loại (type)

| Loại | Khi nào dùng | Ví dụ |
|------|-------------|-------|
| `feat` | Thêm tính năng mới | `feat(queue): add three-lane round-robin scheduler` |
| `fix` | Sửa bug | `fix(patient): fix null pointer on empty phone` |
| `refactor` | Refactor code (không thêm/sửa tính năng) | `refactor(identity): extract JWT logic to util class` |
| `docs` | Thêm/sửa tài liệu | `docs: update API documentation` |
| `chore` | Setup, config, dependencies | `chore: add springdoc dependency to patient-service` |
| `test` | Thêm/sửa test | `test(queue): cover empty-lane round robin` |
| `style` | Format code, thêm dấu ; | `style: format code with IntelliJ` |

### Phạm vi (scope) = tên service

```
identity, patient, appointment, queue, notification,
consultation, prescription, emr, lab, ai, gateway, common
```

### ✅ Tốt

```
feat(queue): implement three scheduling lanes with atomic claim
fix(appointment): return 404 when appointment not found
chore(gateway): add route for lab-service
```

### ❌ Tránh

```
update code               ← không rõ làm gì
fix bug                   ← bug nào?
asdfjkl                   ← ???
```

---

## 5. Giải quyết conflict

### Khi nào xảy ra?

Conflict xảy ra khi **2 người sửa cùng 1 file** cùng lúc. Với cách chia theo phân hệ, conflict sẽ **rất ít** vì mỗi người làm service riêng.

Nơi **dễ conflict** nhất:
- `pom.xml` (parent) — thêm dependency
- `careflow-common/` — thêm DTO, constant
- `docker-compose.infra.yml` — thêm config
- `careflow-api-gateway/application.yml` — thêm route

### Cách giải quyết

```bash
# Khi merge bị conflict
git merge develop
# Git sẽ báo: CONFLICT (content): Merge conflict in <file>

# 1. Mở file bị conflict, tìm đoạn:
<<<<<<< HEAD
    // Code của mình
=======
    // Code từ develop (của người khác)
>>>>>>> develop

# 2. Giữ lại code đúng (thường là giữ CẢ HAI), xóa các dấu <<< === >>>

# 3. Save file, sau đó:
git add <file-bị-conflict>
git commit -m "merge: resolve conflict in <file>"
```

### Mẹo tránh conflict

> ⚠️ **Quy tắc vàng**: Khi cần sửa file chung (`pom.xml`, `common`, `gateway`), **nhắn trong nhóm trước**. Ví dụ: "Tôi đang thêm constant vào AppConstants.java, đợi tôi push xong rồi hãy sửa file này."

---

## 6. Khi nào merge vào main?

### Checklist trước khi merge `develop` → `main`

- [ ] Tất cả services build thành công
- [ ] Các luồng chính chạy được end-to-end
- [ ] Cả 3 người đồng ý "code ổn rồi"
- [ ] `docker-compose up` chạy được toàn bộ

### Cách merge

```bash
git checkout main
git pull origin main
git merge develop
git push origin main
```

### Khi nào nên merge?

| Thời điểm | Merge vào main? |
|-----------|-----------------|
| Cuối tuần 1 (Login hoạt động) | ✅ |
| Cuối tuần 2 (Hàng đợi hoạt động) | ✅ |
| Cuối tuần 3 (Luồng khám hoàn chỉnh) | ✅ |
| Cuối tuần 4 (Hệ thống ổn định) | ✅ |
| Giữa tuần, code đang dở | ❌ |

---

## 7. Setup ban đầu (chạy 1 lần)

### Mỗi người trong nhóm chạy:

```bash
# 1. Clone repo
git clone git@github.com:ntkhai268/careflow.git
cd careflow

# 2. Tạo nhánh develop (người đầu tiên)
git checkout -b develop
git push -u origin develop

# 3. Những người sau chỉ cần checkout
git checkout develop

# 4. Cấu hình git cá nhân
git config user.name "Tên của bạn"
git config user.email "email@example.com"
```

---

## 8. Lệnh hay dùng — Cheat Sheet

### Xem trạng thái

```bash
git status                    # Xem file nào đã thay đổi
git log --oneline -10         # Xem 10 commit gần nhất
git branch -a                 # Xem tất cả nhánh
git diff                      # Xem thay đổi chưa commit
```

### Thao tác nhánh

```bash
git checkout <tên-nhánh>      # Chuyển nhánh
git checkout -b <tên-mới>     # Tạo nhánh mới
git branch -d <tên-nhánh>     # Xóa nhánh local
```

### Lưu code tạm (khi cần chuyển nhánh gấp)

```bash
git stash                     # Cất code tạm
git stash pop                 # Lấy code tạm ra
```

### Hoàn tác

```bash
git checkout -- <file>        # Hoàn tác thay đổi chưa commit của 1 file
git reset HEAD <file>         # Bỏ file ra khỏi staging
git reset --soft HEAD~1       # Hoàn tác commit gần nhất (giữ code)
```

---

## 9. Minh họa 1 tuần làm việc

```
Thứ 2:
  A: git checkout -b feature/a/identity-service
     → code User entity, JWT → commit → push
  B: git checkout -b feature/b/patient-crud
     → code Patient entity, CRUD → commit → push
  C: git checkout -b feature/c/web-login
     → code Login page → commit → push

Thứ 3:
  A: tiếp tục code → commit → push
  B: tiếp tục code → commit → push
  C: tiếp tục code → commit → push

Thứ 4:
  A: hoàn thành → merge vào develop → push develop
  B: git checkout develop → git pull → git checkout feature/b/patient-crud
     → git merge develop (lấy code Identity của A vào)
     → tiếp tục code → commit → push

Thứ 5:
  B: hoàn thành → merge vào develop → push develop
  C: git checkout develop → git pull → lấy code mới vào feature
     → tiếp tục code → commit → push

Thứ 6:
  C: hoàn thành → merge vào develop → push develop
  Cả nhóm: test tổng hợp trên develop
  Nếu ổn → merge develop vào main → push main ✅
```

---

## 10. Nguyên tắc nhóm

1. **Commit thường xuyên** — mỗi lần hoàn thành 1 phần nhỏ là commit, đừng để cuối ngày commit 1 cục to
2. **Push mỗi ngày** — để backup code và cho người khác thấy tiến độ
3. **Pull trước khi code** — luôn `git pull origin develop` đầu ngày
4. **Không push trực tiếp lên main** — luôn qua develop
5. **Nhắn nhóm trước khi sửa file chung** — tránh conflict không cần thiết
6. **Viết commit message rõ ràng** — tương lai đọc lại sẽ cảm ơn bản thân
