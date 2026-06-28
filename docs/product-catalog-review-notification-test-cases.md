# Test cases cho Product Catalog, Detail, Review, Search và Notifications

Tài liệu này được viết dựa trên code hiện tại của backend Spring Boot và frontend Angular.

Nguồn đối chiếu chính:
- Backend: `ProductController`, `ProductService`, `ProductReviewService`, `ProductCategoryController`, `ProductCategoryService`, `NotificationController`, `NotificationService`, `R2StorageService`
- Frontend: `ProductCatalogService`, `ProductListingStore`, `ProductListingPageComponent`, `ProductDetailStore`, `ProductDetailPageComponent`, `AddReviewModalComponent`, `SiteHeaderComponent`, `NotificationService`, `NotificationStore`, `NotificationBellComponent`

## 1. Phạm vi kiểm thử

Các flow được bao phủ:
- Truy cập danh sách sản phẩm public
- Truy cập danh sách sản phẩm theo danh mục
- Xem chi tiết sản phẩm
- Tìm kiếm sản phẩm từ trang `/products` và search overlay ở header
- Xem, tạo, cập nhật, xóa review
- Upload media cho review
- Xem, đánh dấu đã đọc và điều hướng từ notifications

## 2. Endpoint liên quan

| Flow | Method | Endpoint | Auth token |
|---|---:|---|---|
| Lấy cây danh mục | GET | `/api/categories` | Không |
| Sản phẩm theo danh mục | GET | `/api/categories/{categoryId}/products` | Không |
| Danh sách/tìm kiếm sản phẩm | GET | `/api/products` | Không |
| Chi tiết sản phẩm | GET | `/api/products/{productId}` | Không |
| Danh sách review | GET | `/api/products/{productId}/reviews` | Không |
| Tạo review | POST | `/api/products/{productId}/reviews` | Có, customer |
| Cập nhật review | PUT | `/api/products/{productId}/reviews/{reviewId}` | Có, owner |
| Xóa review | DELETE | `/api/products/{productId}/reviews/{reviewId}` | Có, owner |
| Presign upload review media | POST | `/api/uploads/presign` | Có |
| Lấy notifications | GET | `/api/notifications` | Có |
| Đếm notifications chưa đọc | GET | `/api/notifications/unread-count` | Có |
| Đánh dấu 1 notification đã đọc | PUT | `/api/notifications/{id}/read` | Có, owner |
| Đánh dấu tất cả đã đọc | PUT | `/api/notifications/read-all` | Có |
| Realtime notification | WS | `/user/queue/notifications` | Có |

## 3. Query params và rule chính

### Product listing/search

| Param | Rule hiện tại |
|---|---|
| `search` | Trim keyword; rỗng thì xem như không lọc. Backend lọc theo tên sản phẩm. |
| `minRating` | Backend hỗ trợ 1-5; FE public hiện chưa truyền param này. |
| `sort` | `NEWEST`, `OLDEST`, `PRICE_ASC`, `PRICE_DESC`, `RATING_ASC`, `RATING_DESC`. FE public đang map `featured -> NEWEST`, `price-asc -> PRICE_ASC`, `price-desc -> PRICE_DESC`. |
| `page` | >= 0, default 0. |
| `size` | >= 1, default 10 cho product listing, 5 cho review list. |

### Review

| Field | Rule hiện tại |
|---|---|
| `rating` | Bắt buộc, từ 1 đến 5. |
| `comment` | Backend cho phép null/rỗng và trim; FE bắt buộc nhập comment. |
| `imageKeys` | Tối đa 5 ảnh; mỗi ảnh JPEG/PNG/WEBP, <= 5MB, thuộc prefix user hiện tại và tồn tại trên R2. |
| `videoKey` | Tối đa 1 video qua FE; MP4/WEBM, <= 50MB, thuộc prefix user hiện tại và tồn tại trên R2. |
| Quyền sửa/xóa | Chỉ customer owner của review được sửa/xóa. |

## 4. Dữ liệu test đề xuất

| Biến | Giá trị mẫu | Ghi chú |
|---|---|---|
| Product còn hàng | Product active, chưa deleted, có variant stock > 0 | Dùng listing/detail/cart |
| Product hết hàng | Product active, variant stock = 0 | Dùng trạng thái out of stock |
| Product bị xóa | `deleted = true` | Không được xuất hiện ở listing public |
| Category visible | Danh mục `visible = true` | Xuất hiện trong cây danh mục |
| Category hidden | Danh mục `visible = false` | Không xuất hiện public |
| Customer A | Tài khoản role CUSTOMER | Tạo review và nhận notification customer |
| Customer B | Tài khoản role CUSTOMER khác | Test sửa/xóa review không phải owner |
| Staff user | OWNER/MANAGER/EMPLOYEE/ADMIN | Test notification route và không được manage review nếu không có customer profile |
| Ảnh review hợp lệ | JPEG/PNG/WEBP <= 5MB | Test upload |
| Video review hợp lệ | MP4/WEBM <= 50MB | Test upload |

## 5. Test cases danh sách sản phẩm

### PROD-LIST-001 - Truy cập trang `/products` khi chưa đăng nhập (Pass)

Tiền điều kiện: Có ít nhất 1 sản phẩm active, chưa deleted.

Các bước:
1. Mở `/products` ở trạng thái guest.
2. Quan sát request network.

Kết quả mong đợi:
- FE gọi `GET /api/products?search=&sort=NEWEST&page=0&size=10`.
- Request không gắn auth token bắt buộc.
- Danh sách hiển thị sản phẩm public.
- Mỗi item có tên, ảnh hoặc placeholder, giá, trạng thái còn hàng/hết hàng, rating nếu có.

### PROD-LIST-002 - Không hiển thị sản phẩm đã deleted (Pass)

Tiền điều kiện: DB có product `deleted = true`.

Các bước:
1. Gọi `GET /api/products`.
2. Kiểm tra danh sách trả về.

Kết quả mong đợi:
- Product bị deleted không xuất hiện trong response.
- `totalItems` không tính product bị deleted.

### PROD-LIST-003 - Phân trang trang đầu (Pass)
  
Tiền điều kiện: Có hơn 10 sản phẩm active.

Các bước:
1. Gọi `GET /api/products?page=0&size=10`.

Kết quả mong đợi:
- Response có tối đa 10 item.
- `page = 0`, `size = 10`.
- `totalItems` bằng tổng số sản phẩm hợp lệ.
- `totalPages` được tính theo `ceil(totalItems / size)`.
- `hasNext = true` nếu còn trang sau.
- `hasPrevious = false`.

### PROD-LIST-004 - Load more trên UI (Pass)

Tiền điều kiện: Trang `/products` đang có `hasNext = true`.

Các bước:
1. Bấm nút/tác vụ tải thêm sản phẩm.

Kết quả mong đợi:
- FE gọi `GET /api/products` với `page = currentPage + 1`.
- Sản phẩm mới được append vào danh sách hiện tại.
- Không duplicate item theo `product.id`.
- Khi hết trang, UI không tiếp tục gọi load more.

### PROD-LIST-005 - Page vượt quá tổng số trang (Pass)

Các bước:
1. Gọi `GET /api/products?page=999&size=10`.

Kết quả mong đợi:
- Response 200.
- `items` rỗng.
- `hasNext = false`.
- `hasPrevious = true` nếu `page > 0`.
- Không phát sinh lỗi server.

### PROD-LIST-006 - Validate page âm (Pass)

Các bước:
1. Gọi `GET /api/products?page=-1&size=10`.

Kết quả mong đợi:
- Backend trả lỗi validation `page must be greater than or equal to 0`.

### PROD-LIST-007 - Validate size bằng 0 (Pass)

Các bước:
1. Gọi `GET /api/products?page=0&size=0`.

Kết quả mong đợi:
- Backend trả lỗi validation `size must be greater than 0`.

### PROD-LIST-008 - Sort mới nhất (UI Chưa có)

Các bước:
1. Gọi `GET /api/products?sort=NEWEST`.

Kết quả mong đợi:
- Sản phẩm được sort theo `createdAt` giảm dần.
- Nếu trùng thời gian, thứ tự ổn định theo `productId`.

### PROD-LIST-009 - Sort cũ nhất (UI chưa có)

Các bước:
1. Gọi `GET /api/products?sort=OLDEST`.

Kết quả mong đợi:
- Sản phẩm được sort theo `createdAt` tăng dần.

### PROD-LIST-010 - Sort giá tăng dần (Pass)

Các bước:
1. Gọi `GET /api/products?sort=PRICE_ASC`.

Kết quả mong đợi:
- Sản phẩm được sort theo giá hiệu lực tăng dần.
- Giá hiệu lực là `salePrice` nếu có, ngược lại `originalPrice`.

### PROD-LIST-011 - Sort giá giảm dần (Pass)

Các bước:
1. Gọi `GET /api/products?sort=PRICE_DESC`.

Kết quả mong đợi:
- Sản phẩm được sort theo giá hiệu lực giảm dần.

### PROD-LIST-012 - Sort rating tăng/giảm dần (UI chưa có)

Các bước:
1. Gọi `GET /api/products?sort=RATING_ASC`.
2. Gọi `GET /api/products?sort=RATING_DESC`.

Kết quả mong đợi:
- `RATING_ASC` sort rating thấp đến cao.
- `RATING_DESC` sort rating cao đến thấp.
- Product chưa có rating được đưa về cuối theo comparator `nullsLast`.

### PROD-LIST-013 - Sort không hợp lệ (Pass)

Các bước:
1. Gọi `GET /api/products?sort=INVALID_SORT`.

Kết quả mong đợi:
- Backend trả lỗi bind/validation cho enum không hợp lệ.
- Không trả dữ liệu sai mặc định âm thầm.

### PROD-LIST-014 - Lọc minRating hợp lệ qua API (UI chưa có)

Các bước:
1. Gọi `GET /api/products?minRating=4`.

Kết quả mong đợi:
- Chỉ trả sản phẩm có `averageRating >= 4`.
- Product chưa có review/rating không xuất hiện.

### PROD-LIST-015 - Validate minRating ngoài khoảng (Pass)

Các bước:
1. Gọi `GET /api/products?minRating=0`.
2. Gọi `GET /api/products?minRating=6`.

Kết quả mong đợi:
- Backend trả lỗi validation `minRating must be between 1 and 5`.

## 6. Test cases danh sách sản phẩm theo danh mục

### PROD-CAT-001 - Lấy cây danh mục public (Pass)

Các bước:
1. Gọi `GET /api/categories`.

Kết quả mong đợi:
- Chỉ trả danh mục `visible = true`.
- Danh mục cha/con được dựng đúng cây.
- Danh mục được sort theo `priority`, sau đó `categoryName`, sau đó `id`.

### PROD-CAT-002 - Truy cập danh mục hợp lệ từ UI (Pass)

Tiền điều kiện: Có danh mục public slug hợp lệ.

Các bước:
1. Mở `/categories/{slug}`.

Kết quả mong đợi:
- FE resolve slug sang category từ cây danh mục.
- FE gọi `GET /api/categories/{categoryId}/products?page=0&size=10&sort=NEWEST`.
- Hiển thị sản phẩm thuộc category đó và các category con.

### PROD-CAT-003 - Danh mục không tồn tại (Pass)

Các bước:
1. Mở `/categories/not-found-slug`.

Kết quả mong đợi:
- FE hiển thị trạng thái danh mục không tồn tại.
- Không hiển thị danh sách cũ.

### PROD-CAT-004 - Category hidden không truy cập được public (Pass)

Tiền điều kiện: Có category `visible = false`.

Các bước:
1. Gọi `GET /api/categories/{hiddenCategoryId}/products`.

Kết quả mong đợi:
- Backend trả not found.
- Không lộ sản phẩm của danh mục hidden qua endpoint public.

### PROD-CAT-005 - Product thuộc category con xuất hiện khi xem category cha (Pass)

Tiền điều kiện: Category cha public có category con public, product gắn với category con.

Các bước:
1. Gọi `GET /api/categories/{parentCategoryId}/products`.

Kết quả mong đợi:
- Product thuộc category con xuất hiện trong danh sách category cha.
- Product không bị duplicate nếu gắn nhiều category trong cây.

### PROD-CAT-006 - Sort theo danh mục (Pass)

Các bước:
1. Mở `/categories/{slug}`.
2. Chọn sort giá tăng.
3. Chọn sort giá giảm.

Kết quả mong đợi:
- FE gọi API category với `sort=PRICE_ASC` và `sort=PRICE_DESC`.
- Danh sách được refresh từ page 0.
- UI không append dữ liệu sort mới vào danh sách cũ.

## 7. Test cases tìm kiếm sản phẩm

### PROD-SEARCH-001 - Tìm kiếm từ header overlay (Hiện tại - chỉ có tìm kiếm sản phẩm)

Các bước:
1. Bấm icon search trên header.
2. Nhập keyword hợp lệ, ví dụ `keyboard`.
3. Đợi debounce.

Kết quả mong đợi:
- Sau khoảng 300ms, FE gọi `GET /api/products?search=keyboard&sort=NEWEST&page=0&size=5`.
- Overlay hiển thị tối đa 5 sản phẩm gợi ý.
- Có loading state trong khi chờ response.

### PROD-SEARCH-002 - Keyword rỗng trong overlay (pass)

Các bước:
1. Mở search overlay.
2. Để trống input hoặc chỉ nhập khoảng trắng.

Kết quả mong đợi:
- FE không gọi API tìm kiếm.
- Overlay hiển thị placeholder.
- Danh sách gợi ý được clear.

### PROD-SEARCH-003 - Enter để xem tất cả kết quả (pass)

Các bước:
1. Mở search overlay.
2. Nhập `keyboard`.
3. Bấm Enter hoặc bấm footer xem tất cả.

Kết quả mong đợi:
- Overlay đóng.
- FE điều hướng tới `/products?search=keyboard`.
- Trang `/products` gọi `GET /api/products?search=keyboard&sort=NEWEST&page=0&size=10`.

### PROD-SEARCH-004 - Click sản phẩm gợi ý (pass)

Tiền điều kiện: Overlay có ít nhất 1 kết quả.

Các bước:
1. Click một sản phẩm gợi ý.

Kết quả mong đợi:
- Overlay đóng.
- FE điều hướng tới `/products/{productId}`.
- Trang detail tải đúng sản phẩm.

### PROD-SEARCH-005 - Tìm kiếm không phân biệt hoa thường (pass)

Các bước:
1. Gọi `GET /api/products?search=KEYBOARD`.
2. Gọi `GET /api/products?search=keyboard`.

Kết quả mong đợi:
- Hai kết quả tương đương về tập sản phẩm nếu tên sản phẩm chứa keyword.

### PROD-SEARCH-006 - Trim keyword (Chưa trim được)

Các bước:
1. Gọi `GET /api/products?search=%20%20keyboard%20%20`.

Kết quả mong đợi:
- Backend xử lý như keyword `keyboard`.

### PROD-SEARCH-007 - Không có kết quả (pass)

Các bước:
1. Mở `/products?search=keyword-khong-ton-tai`.

Kết quả mong đợi:
- Response 200 với `items = []`.
- UI hiển thị empty state.
- Không hiển thị dữ liệu từ lần tìm kiếm trước.

### PROD-SEARCH-008 - Lỗi API khi search overlay (pass)

Các bước:
1. Giả lập `GET /api/products` lỗi khi đang search overlay.

Kết quả mong đợi:
- Loading tắt.
- Overlay không crash.
- Danh sách kết quả không hiển thị dữ liệu sai.

## 8. Test cases chi tiết sản phẩm

### PROD-DETAIL-001 - Xem chi tiết sản phẩm hợp lệ (pass)

Tiền điều kiện: Product tồn tại, chưa deleted.

Các bước:
1. Mở `/products/{productId}`.

Kết quả mong đợi:
- FE gọi song song:
  - `GET /api/products/{productId}`
  - `GET /api/products/{productId}/reviews?page=0&size=5`
- Hiển thị gallery, thông tin sản phẩm, variants, giá, tồn kho, specs, rating trung bình, tổng review.
- Variant đầu tiên được chọn mặc định.
- Quantity mặc định là 1 nếu variant còn hàng, 0 nếu hết hàng.

### PROD-DETAIL-002 - Product không tồn tại (pass)

Các bước:
1. Mở `/products/{uuid-khong-ton-tai}`.

Kết quả mong đợi:
- Backend trả not found.
- FE hiển thị thông báo sản phẩm chưa tồn tại.
- Không hiển thị dữ liệu detail cũ.

### PROD-DETAIL-003 - Product không có ảnh (pass)

Tiền điều kiện: Product không có `imageKeys`.

Các bước:
1. Mở detail product.

Kết quả mong đợi:
- Backend trả `productImageUrls` rỗng hoặc null.
- FE dùng placeholder `/home/asset-1.webp`.
- Gallery không bị lỗi ảnh broken.

### PROD-DETAIL-004 - Product có nhiều ảnh (pass)

Tiền điều kiện: Product có nhiều `imageKeys`.

Các bước:
1. Mở detail product.
2. Click từng thumbnail/gallery item.

Kết quả mong đợi:
- Backend trả URL ảnh public/presigned tương ứng.
- FE hiển thị ảnh đang chọn đúng.
- Không duplicate ảnh trong gallery.

### PROD-DETAIL-005 - Chọn variant còn hàng (pass)

Tiền điều kiện: Product có nhiều variant, ít nhất 1 variant còn hàng.

Các bước:
1. Mở detail.
2. Chọn variant còn hàng.

Kết quả mong đợi:
- Giá hiển thị theo variant đã chọn.
- Nếu có `salePrice`, hiển thị giá sale và giá gốc.
- Stock và quantity max cập nhật theo variant.

### PROD-DETAIL-006 - Chọn variant hết hàng (pass)

Tiền điều kiện: Product có variant stock = 0.

Các bước:
1. Chọn variant hết hàng.

Kết quả mong đợi:
- Quantity reset về 0.
- Không thể tăng quantity.
- Khi thêm giỏ/mua ngay, FE cảnh báo chọn variant còn hàng.

### PROD-DETAIL-007 - Tăng giảm quantity trong giới hạn stock (pass)

Các bước:
1. Chọn variant có stock = N.
2. Bấm tăng quantity nhiều lần.
3. Bấm giảm quantity nhiều lần.

Kết quả mong đợi:
- Quantity không vượt quá N.
- Quantity không thấp hơn 1 nếu variant còn hàng.

### PROD-DETAIL-008 - Sản phẩm cùng nhóm (pass)

Tiền điều kiện: Product thuộc product group có sản phẩm khác.

Các bước:
1. Mở detail product.

Kết quả mong đợi:
- Backend trả `groupProducts` không bao gồm product hiện tại.
- Group products sort theo `productName`, sau đó `id`.
- FE hiển thị sản phẩm cùng nhóm với ảnh đại diện.

### PROD-DETAIL-009 - Sản phẩm tương tự (pass)

Tiền điều kiện: Có sản phẩm cùng category với product hiện tại.

Các bước:
1. Mở detail product.

Kết quả mong đợi:
- Backend trả tối đa 4 `similarProducts`.
- Không bao gồm product hiện tại.
- Ưu tiên sản phẩm chung category, giá gần, rating cao, mới hơn.

### PROD-DETAIL-010 - Danh sách review lỗi nhưng detail vẫn tải (pass)

Các bước:
1. Giả lập `GET /api/products/{id}` thành công.
2. Giả lập `GET /api/products/{id}/reviews` lỗi.

Kết quả mong đợi:
- FE vẫn hiển thị detail product.
- Reviews hiển thị rỗng.
- Trang không crash.

## 9. Test cases review

### PROD-REVIEW-001 - Xem danh sách review public (pass)

Tiền điều kiện: Product có nhiều review.

Các bước:
1. Gọi `GET /api/products/{productId}/reviews?page=0&size=5`.

Kết quả mong đợi:
- Response 200.
- Reviews sort theo `createdAt DESC`, `id DESC`.
- Mỗi item có `reviewId`, `rating`, `comment`, `customerName`, `createdAt`, `isOwner`, `imageUrls`, `videoUrl`.
- Guest xem được review, `isOwner = false`.

### PROD-REVIEW-002 - Review list product không tồn tại (pass)

Các bước:
1. Gọi `GET /api/products/{productId-khong-ton-tai}/reviews`.

Kết quả mong đợi:
- Backend trả not found.

### PROD-REVIEW-003 - Validate review page âm (pass)

Các bước:
1. Gọi `GET /api/products/{productId}/reviews?page=-1`.

Kết quả mong đợi:
- Backend trả lỗi validation.

### PROD-REVIEW-004 - Validate review size bằng 0 (pass)

Các bước:
1. Gọi `GET /api/products/{productId}/reviews?size=0`.

Kết quả mong đợi:
- Backend trả lỗi validation.

### PROD-REVIEW-005 - Guest mở modal review (UI chưa xử lý khi chưa đăng nhập thì nút viết đánh giá đổi thành đăng nhập để viết đánh giá)

Các bước:
1. Mở detail product khi chưa đăng nhập.
2. Thử bấm viết review nếu UI cho phép.

Kết quả mong đợi:
- Nếu gọi submit review trực tiếp không có token, backend trả 401/403.
- Không tạo review.

### PROD-REVIEW-006 - Customer tạo review thành công chỉ có text (pass)

Tiền điều kiện: Customer A đã đăng nhập, product tồn tại.

Các bước:
1. Mở detail product.
2. Mở modal review.
3. Chọn rating 5.
4. Nhập comment hợp lệ.
5. Không chọn media.
6. Submit.

Kết quả mong đợi:
- FE gọi `POST /api/products/{productId}/reviews`.
- Payload có `rating`, `comment.trim()`, `imageKeys = []`, không có `videoKey`.
- Backend tạo review gắn product và customer hiện tại.
- FE thêm review mới vào đầu danh sách local.
- Rating/reviewCount local được cập nhật.
- Toast thành công.

### PROD-REVIEW-007 - FE chặn submit khi chưa chọn sao (pass)

Các bước:
1. Mở modal review.
2. Nhập comment.
3. Không chọn rating.
4. Submit.

Kết quả mong đợi:
- FE hiển thị lỗi chọn số sao.
- Không gọi API tạo review.

### PROD-REVIEW-008 - FE chặn submit khi comment rỗng (pass)

Các bước:
1. Chọn rating.
2. Để comment rỗng hoặc chỉ khoảng trắng.
3. Submit.

Kết quả mong đợi:
- FE hiển thị lỗi nhập nội dung đánh giá.
- Không gọi API tạo review.

### PROD-REVIEW-009 - API tạo review với rating rỗng (pass)

Các bước:
1. Gọi `POST /api/products/{productId}/reviews` không có `rating`.

Kết quả mong đợi:
- Backend trả lỗi validation `rating is required`.
- Không tạo review.

### PROD-REVIEW-010 - API tạo review với rating ngoài khoảng (pass)

Các bước:
1. Gọi API với `rating = 0`.
2. Gọi API với `rating = 6`.

Kết quả mong đợi:
- Backend trả lỗi validation `rating must be between 1 and 5`.
- Không tạo review.

### PROD-REVIEW-011 - Customer tạo review với ảnh hợp lệ (pass)

Tiền điều kiện: Customer A đã đăng nhập.

Các bước:
1. Trong modal review, chọn ảnh JPEG/PNG/WEBP <= 5MB.
2. Submit review.

Kết quả mong đợi:
- FE gọi `POST /api/uploads/presign` với `purpose = PRODUCT_REVIEW`.
- FE PUT file lên R2 bằng presigned URL, bỏ qua JWT hệ thống.
- FE gửi `imageKeys` đã upload trong payload review.
- Backend validate key thuộc user hiện tại, object tồn tại, đúng content type và size.
- Review được tạo và trả `imageUrls`.

### PROD-REVIEW-012 - FE chặn ảnh sai định dạng (pass)

Các bước:
1. Chọn file ảnh định dạng không thuộc JPEG/PNG/WEBP.

Kết quả mong đợi:
- FE hiển thị lỗi chỉ hỗ trợ JPEG, PNG hoặc WEBP.
- Không gọi presign/upload.

### PROD-REVIEW-013 - FE chặn ảnh vượt quá 5MB (pass)

Các bước:
1. Chọn ảnh > 5MB.

Kết quả mong đợi:
- FE hiển thị lỗi ảnh không vượt quá 5MB.
- Không gọi presign/upload.

### PROD-REVIEW-014 - FE chặn quá số lượng media (pass)

Các bước:
1. Chọn hơn 5 ảnh khi chưa có video.
2. Chọn 5 ảnh rồi chọn thêm video.
3. Chọn 1 video rồi chọn hơn 4 ảnh.

Kết quả mong đợi:
- Tổng media tối đa là 5.
- Nếu có video, số ảnh tối đa còn 4.
- FE hiển thị lỗi giới hạn media.

### PROD-REVIEW-015 - Customer tạo review với video hợp lệ (pass)

Tiền điều kiện: Customer A đã đăng nhập.

Các bước:
1. Chọn video MP4/WEBM <= 50MB.
2. Submit review.

Kết quả mong đợi:
- FE gọi presign với `purpose = PRODUCT_REVIEW_VIDEO`.
- FE upload file lên R2.
- Payload review có `videoKey`.
- Backend validate video key thuộc user hiện tại, object tồn tại, đúng content type và size.
- Review trả về `videoUrl`.

### PROD-REVIEW-016 - FE chặn video sai định dạng (pass)

Các bước:
1. Chọn video không phải MP4/WEBM.

Kết quả mong đợi:
- FE hiển thị lỗi chỉ hỗ trợ video MP4 hoặc WEBM.
- Không gọi presign/upload.

### PROD-REVIEW-017 - FE chặn video vượt quá 50MB (pass)

Các bước:
1. Chọn video > 50MB.

Kết quả mong đợi:
- FE hiển thị lỗi video không vượt quá 50MB.
- Không gọi presign/upload.

### PROD-REVIEW-018 - Upload media lỗi khi submit review (pass)

Các bước:
1. Chọn ảnh/video hợp lệ.
2. Giả lập presign hoặc PUT R2 thất bại.
3. Submit.

Kết quả mong đợi:
- FE hiển thị lỗi không thể tải media lên.
- Không gọi API tạo review nếu media chưa upload thành công.
- Modal vẫn giữ trạng thái để user thử lại.

### PROD-REVIEW-019 - API từ chối imageKey không thuộc user hiện tại (pass)

Các bước:
1. Customer A gửi review với `imageKeys` có prefix của Customer B.

Kết quả mong đợi:
- Backend trả lỗi `Invalid image key owner`.
- Không tạo review.

### PROD-REVIEW-020 - API từ chối imageKey không tồn tại trên R2 (pass)

Các bước:
1. Gửi review với `imageKeys` đúng prefix nhưng object không tồn tại.

Kết quả mong đợi:
- Backend trả lỗi uploaded image does not exist.
- Không tạo review.

### PROD-REVIEW-021 - Owner cập nhật review thành công (pass)

Tiền điều kiện: Customer A đã tạo review.

Các bước:
1. Customer A gọi `PUT /api/products/{productId}/reviews/{reviewId}` với rating/comment mới.

Kết quả mong đợi:
- Backend xác nhận review thuộc product và owner là Customer A.
- Review được cập nhật rating/comment/media.
- Response trả review mới.

### PROD-REVIEW-022 - Không cho user khác cập nhật review (UI chưa có nút edit)

Tiền điều kiện: Review thuộc Customer A, Customer B đã đăng nhập.

Các bước:
1. Customer B gọi API cập nhật review của Customer A.

Kết quả mong đợi:
- Backend trả access denied.
- Review không thay đổi.

### PROD-REVIEW-023 - Owner xóa review thành công (UI chưa có tính năng này)

Tiền điều kiện: Review thuộc Customer A.

Các bước:
1. Customer A gọi `DELETE /api/products/{productId}/reviews/{reviewId}`.

Kết quả mong đợi:
- Backend xóa review.
- Response 200 với thông báo `Review deleted successfully`.
- Review không còn trong danh sách.

### PROD-REVIEW-024 - Không cho user khác xóa review (UI chưa có)

Tiền điều kiện: Review thuộc Customer A, Customer B đã đăng nhập.

Các bước:
1. Customer B gọi API xóa review của Customer A.

Kết quả mong đợi:
- Backend trả access denied.
- Review vẫn tồn tại.

### PROD-REVIEW-025 - Staff/Admin không có customer profile không được tạo review (pass)

Tiền điều kiện: Đăng nhập bằng account không có Customer profile.

Các bước:
1. Gọi `POST /api/products/{productId}/reviews`.

Kết quả mong đợi:
- Backend trả access denied `Only customers can manage reviews`.
- Không tạo review.

## 10. Test cases notifications

### NOTIF-001 - User đã đăng nhập lấy danh sách notifications (pass)

Tiền điều kiện: User có notifications.

Các bước:
1. Đăng nhập.
2. Mở UI có notification bell hoặc gọi `GET /api/notifications?page=0&size=20`.

Kết quả mong đợi:
- Response chỉ chứa notifications của account hiện tại.
- Sort theo `createdAt DESC`.
- FE store lưu tối đa page hiện tại và hiển thị trên bell/popover.

### NOTIF-002 - Guest không được lấy notifications (pass)

Các bước:
1. Gọi `GET /api/notifications` không có token.

Kết quả mong đợi:
- Backend trả 401 Unauthorized.

### NOTIF-003 - Lấy unread count

Tiền điều kiện: User có N notification `isRead = false`.

Các bước:
1. Gọi `GET /api/notifications/unread-count`.

Kết quả mong đợi:
- Response `{ "count": N }`.
- FE cập nhật badge unread count.

### NOTIF-004 - Mark one notification as read thành công (pass)

Tiền điều kiện: Notification thuộc user hiện tại, `isRead = false`.

Các bước:
1. Click notification trên bell hoặc gọi `PUT /api/notifications/{id}/read`.

Kết quả mong đợi:
- Backend set `isRead = true`.
- FE optimistic update notification đó thành read.
- `unreadCount` giảm 1 nhưng không thấp hơn 0.

### NOTIF-005 - Không được mark notification của user khác (Bug - vô account khác nhau trên cùng 1 trình duyệt => thấy noti của người khác)

Tiền điều kiện: Notification thuộc User A, User B đã đăng nhập.

Các bước:
1. User B gọi `PUT /api/notifications/{notificationIdOfA}/read`.

Kết quả mong đợi:
- Backend trả lỗi không có quyền.
- Notification của User A không bị đổi trạng thái.

### NOTIF-006 - Mark notification không tồn tại (pass)

Các bước:
1. Gọi `PUT /api/notifications/{uuid-khong-ton-tai}/read`.

Kết quả mong đợi:
- Backend trả not found.

### NOTIF-007 - Mark all as read (pass)

Tiền điều kiện: User có nhiều notification chưa đọc.

Các bước:
1. Click "mark all as read" hoặc gọi `PUT /api/notifications/read-all`.

Kết quả mong đợi:
- Backend mark tất cả notification của account hiện tại thành read.
- FE optimistic update toàn bộ entity thành `isRead = true`.
- `unreadCount = 0`.

### NOTIF-008 - Mark all không ảnh hưởng user khác (pass neeus ko bị bug NOTIF-005)

Tiền điều kiện: User A và User B đều có notification chưa đọc.

Các bước:
1. User A gọi `PUT /api/notifications/read-all`.
2. Kiểm tra notification của User B.

Kết quả mong đợi:
- Chỉ notification của User A được mark read.
- Notification của User B giữ nguyên trạng thái.

### NOTIF-009 - Nhận notification realtime qua WebSocket (pass)

Tiền điều kiện: User đã đăng nhập, WebSocket connected.

Các bước:
1. Trigger nghiệp vụ tạo notification cho user.
2. Lắng nghe `/user/queue/notifications`.

Kết quả mong đợi:
- Backend gửi notification tới user theo email account.
- FE nhận notification mới, add vào store.
- `unreadCount` tăng 1.
- Notification mới nằm đầu danh sách theo thời gian.

### NOTIF-010 - Lỗi API load notifications (pass)

Các bước:
1. Giả lập `GET /api/notifications` lỗi.

Kết quả mong đợi:
- FE tắt loading.
- Không crash notification bell.
- Không xóa nhầm dữ liệu hiện có nếu không có response mới.

### NOTIF-011 - Click notification loại CHAT_MESSAGE ở customer site (pass)

Tiền điều kiện: Notification type `CHAT_MESSAGE`, user đang ở site customer.

Các bước:
1. Click notification.

Kết quả mong đợi:
- FE mark as read nếu chưa đọc.
- Popover đóng.
- FE điều hướng tới `/chat`.

### NOTIF-012 - Click notification loại CHAT_MESSAGE ở management (pass)

Tiền điều kiện: Notification type `CHAT_MESSAGE`, URL hiện tại bắt đầu bằng `/management`.

Các bước:
1. Click notification.

Kết quả mong đợi:
- FE điều hướng tới `/management/chat?conversationId={referenceId}`.

### NOTIF-013 - Click notification loại AGENT_REQUEST/CONVERSATION_TRANSFER (pass)

Các bước:
1. Click notification có type `AGENT_REQUEST` hoặc `CONVERSATION_TRANSFER`.

Kết quả mong đợi:
- FE điều hướng tới `/management/chat?conversationId={referenceId}`.

### NOTIF-014 - Click notification loại REQUEST_SUBMITTED (pass)

Các bước:
1. Click notification type `REQUEST_SUBMITTED`.

Kết quả mong đợi:
- FE điều hướng tới `/management/approvals`.

### NOTIF-015 - Click notification loại REQUEST_APPROVED/REQUEST_REJECTED (pass)

Các bước:
1. Click notification type `REQUEST_APPROVED` hoặc `REQUEST_REJECTED`.

Kết quả mong đợi:
- FE điều hướng tới `/management/requests`.

### NOTIF-016 - Click notification loại WORK_SCHEDULE (pass)

Các bước:
1. Click notification type `WORK_SCHEDULE`.

Kết quả mong đợi:
- FE điều hướng tới `/management/work-schedules`.

### NOTIF-017 - Click notification loại ORDER_STATUS với customer (pass)

Tiền điều kiện: Current user role chứa `CUSTOMER`.

Các bước:
1. Click notification type `ORDER_STATUS`.

Kết quả mong đợi:
- FE điều hướng tới `/account/orders`.

### NOTIF-018 - Click notification loại ORDER_STATUS với staff (pass)

Tiền điều kiện: Current user không phải customer.

Các bước:
1. Click notification type `ORDER_STATUS`.

Kết quả mong đợi:
- FE điều hướng tới `/management/orders`.

### NOTIF-019 - Notification type chưa có route riêng (pass)

Các bước:
1. Click notification type `SYSTEM` hoặc `PROMOTION`.

Kết quả mong đợi:
- FE vẫn mark read nếu chưa đọc và đóng popover.
- Không điều hướng sai route.
- Không crash.

## 11. Test cases bảo mật và regression chung

### PROD-SEC-001 - Public catalog không phụ thuộc session (pass)

Các bước:
1. Gọi `/api/products`, `/api/categories`, `/api/products/{id}`, `/api/products/{id}/reviews` khi guest.

Kết quả mong đợi:
- Các API public trả dữ liệu hợp lệ.
- Không yêu cầu access token.

### PROD-SEC-002 - Action có ghi dữ liệu phải cần auth (pass)

Các bước:
1. Gọi create/update/delete review không có token.
2. Gọi notification API không có token.

Kết quả mong đợi:
- Backend từ chối 401/403.
- Không thay đổi dữ liệu.

### PROD-SEC-003 - Review media không chấp nhận key giả (pass)

Các bước:
1. Gửi review với `imageKeys`/`videoKey` tự chế, không qua presign/upload hợp lệ.

Kết quả mong đợi:
- Backend validate owner, object tồn tại, content type, size.
- Review không được tạo nếu key không hợp lệ.

### PROD-SEC-004 - Không lộ notification cross-account (pass)

Các bước:
1. User A và User B đăng nhập lần lượt.
2. Gọi danh sách notification và unread count.

Kết quả mong đợi:
- Mỗi user chỉ thấy notification của chính mình.
- `markAsRead` có kiểm tra owner.

### PROD-SEC-005 - UI không giữ dữ liệu cũ sau lỗi load (pass)

Các bước:
1. Load thành công product/listing A.
2. Điều hướng sang product/category/search khác và giả lập API lỗi.

Kết quả mong đợi:
- Store clear dữ liệu cũ ở đầu quá trình load.
- UI hiển thị loading/error/empty đúng, không hiển thị dữ liệu stale như là kết quả mới.

## 12. Test cases nên tự động hóa trước

Ưu tiên automated tests:
- `PROD-LIST-001`, `PROD-LIST-003`, `PROD-LIST-006`, `PROD-LIST-010`, `PROD-LIST-014`
- `PROD-CAT-001`, `PROD-CAT-002`, `PROD-CAT-004`, `PROD-CAT-005`
- `PROD-SEARCH-001`, `PROD-SEARCH-003`, `PROD-SEARCH-007`
- `PROD-DETAIL-001`, `PROD-DETAIL-002`, `PROD-DETAIL-005`, `PROD-DETAIL-010`
- `PROD-REVIEW-001`, `PROD-REVIEW-006`, `PROD-REVIEW-009`, `PROD-REVIEW-019`, `PROD-REVIEW-022`, `PROD-REVIEW-023`
- `NOTIF-001`, `NOTIF-003`, `NOTIF-004`, `NOTIF-005`, `NOTIF-007`, `NOTIF-009`

Ghi chú:
- Với E2E review có media, nên mock presign/R2 nếu môi trường test không có Cloudflare R2.
- Với notification realtime, nên có test service-level cho `createNotification` và test frontend store bằng mock WebSocket.
- Với search/listing, cần seed dữ liệu có đủ giá sale, giá gốc, rating null, rating cao/thấp, stock 0 và product deleted để bắt được các nhánh sort/filter.
