SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    -- 1) Đổi tên 2 danh mục mới sang tiếng Việt có dấu
    UPDATE Category
    SET CategoryName = N'Kinh doanh',
        [Description] = N'Sách về kinh doanh, quản trị và khởi nghiệp.'
    WHERE CategoryName = N'Business' OR CategoryID = 5;

    UPDATE Category
    SET CategoryName = N'Tâm lý học',
        [Description] = N'Sách về tâm lý học, cảm xúc và phát triển bản thân.'
    WHERE CategoryName = N'Psychology' OR CategoryID = 6;

    -- 2) Chuẩn hóa tên và mô tả 27 sách đã thêm (BookID 5 -> 31)
    UPDATE Book SET Title = N'Không Gia Đình', [Description] = N'Tiểu thuyết kinh điển về tuổi thơ và nghị lực sống.' WHERE BookID = 5;
    UPDATE Book SET Title = N'Những Người Khốn Khổ', [Description] = N'Tác phẩm kinh điển về công lý, tình thương và chuộc lỗi.' WHERE BookID = 6;
    UPDATE Book SET Title = N'Don Quixote', [Description] = N'Tiểu thuyết phiêu lưu kinh điển với góc nhìn châm biếm sâu sắc.' WHERE BookID = 7;
    UPDATE Book SET [Description] = N'Tiểu thuyết tình cảm cổ điển về định kiến và tình yêu.' WHERE BookID = 8;
    UPDATE Book SET [Description] = N'Tiểu thuyết hiện đại kinh điển phản ánh xã hội thượng lưu Mỹ.' WHERE BookID = 9;
    UPDATE Book SET [Description] = N'Tiểu thuyết phản địa đàng nổi tiếng về kiểm soát và tự do.' WHERE BookID = 10;

    UPDATE Book SET [Description] = N'Sách kỹ năng giúp xây dựng thói quen hiệu quả và tư duy dài hạn.' WHERE BookID = 11;
    UPDATE Book SET [Description] = N'Phương pháp xây dựng thói quen tốt và loại bỏ thói quen xấu.' WHERE BookID = 12;
    UPDATE Book SET [Description] = N'Rèn luyện khả năng tập trung sâu trong môi trường nhiều xao nhãng.' WHERE BookID = 13;
    UPDATE Book SET [Description] = N'Khuyến khích sống trọn vẹn ở hiện tại để giảm căng thẳng.' WHERE BookID = 14;
    UPDATE Book SET [Description] = N'Cuốn sách nổi tiếng về tư duy phát triển và tinh thần học hỏi.' WHERE BookID = 15;
    UPDATE Book SET [Description] = N'Khám phá triết lý sống hạnh phúc và bền vững của người Nhật.' WHERE BookID = 16;

    UPDATE Book SET [Description] = N'Nguyên tắc viết mã sạch, dễ đọc, dễ bảo trì cho lập trình viên.' WHERE BookID = 17;
    UPDATE Book SET [Description] = N'Tổng hợp các mẫu thiết kế phần mềm kinh điển và ứng dụng thực tế.' WHERE BookID = 18;
    UPDATE Book SET [Description] = N'Hướng dẫn cải tiến mã nguồn an toàn thông qua tái cấu trúc.' WHERE BookID = 19;
    UPDATE Book SET [Description] = N'Tài liệu thực chiến để phát triển ứng dụng với Spring Framework.' WHERE BookID = 20;
    UPDATE Book SET [Description] = N'Các thực hành tốt nhất để lập trình Java hiệu quả và an toàn.' WHERE BookID = 21;

    UPDATE Book SET [Description] = N'Phương pháp khởi nghiệp tinh gọn dựa trên kiểm chứng thực tế.' WHERE BookID = 22;
    UPDATE Book SET [Description] = N'Tư duy tạo khác biệt đột phá từ con số 0 đến 1 trong kinh doanh.' WHERE BookID = 23;
    UPDATE Book SET [Description] = N'Bài học xây dựng doanh nghiệp vĩ đại từ nghiên cứu dài hạn.' WHERE BookID = 24;
    UPDATE Book SET [Description] = N'Chia sẻ thực tế về điều hành startup trong giai đoạn khó khăn.' WHERE BookID = 25;
    UPDATE Book SET [Description] = N'Chiến lược tạo thị trường mới và thoát khỏi cạnh tranh khốc liệt.' WHERE BookID = 26;

    UPDATE Book SET [Description] = N'Phân tích thiên kiến nhận thức và cơ chế ra quyết định của con người.' WHERE BookID = 27;
    UPDATE Book SET [Description] = N'Hành trình tìm ý nghĩa sống qua trải nghiệm sinh tồn khắc nghiệt.' WHERE BookID = 28;
    UPDATE Book SET [Description] = N'Giải thích tác động của sang chấn tâm lý lên cơ thể và cảm xúc.' WHERE BookID = 29;
    UPDATE Book SET [Description] = N'Cuốn sách nền tảng về trí tuệ cảm xúc trong công việc và cuộc sống.' WHERE BookID = 30;
    UPDATE Book SET [Description] = N'Phân tích nỗi lo về địa vị xã hội và cách sống bình an hơn.' WHERE BookID = 31;

    -- 3) Thêm vài sách mới (Việt Nam + nước ngoài), mô tả tiếng Việt có dấu
    DECLARE @CatNovel BIGINT = 1;
    DECLARE @CatSkill BIGINT = 2;
    DECLARE @CatTech BIGINT = 3;
    DECLARE @CatBusiness BIGINT = (SELECT TOP 1 CategoryID FROM Category WHERE CategoryName = N'Kinh doanh');
    DECLARE @CatPsychology BIGINT = (SELECT TOP 1 CategoryID FROM Category WHERE CategoryName = N'Tâm lý học');

    IF NOT EXISTS (SELECT 1 FROM Book WHERE Title = N'Tôi Thấy Hoa Vàng Trên Cỏ Xanh' AND Author = N'Nguyễn Nhật Ánh')
    INSERT INTO Book (Title, Author, Price, StockQuantity, [Description], ImageURL, CreatedAt, CategoryID)
    VALUES (N'Tôi Thấy Hoa Vàng Trên Cỏ Xanh', N'Nguyễn Nhật Ánh', 115000, 26, N'Tác phẩm Việt Nam giàu cảm xúc về tuổi thơ, gia đình và tình bạn.', NULL, GETDATE(), @CatNovel);

    IF NOT EXISTS (SELECT 1 FROM Book WHERE Title = N'Mắt Biếc' AND Author = N'Nguyễn Nhật Ánh')
    INSERT INTO Book (Title, Author, Price, StockQuantity, [Description], ImageURL, CreatedAt, CategoryID)
    VALUES (N'Mắt Biếc', N'Nguyễn Nhật Ánh', 109000, 24, N'Tiểu thuyết Việt Nam nổi tiếng về tình yêu đơn phương và ký ức học trò.', NULL, GETDATE(), @CatNovel);

    IF NOT EXISTS (SELECT 1 FROM Book WHERE Title = N'Dế Mèn Phiêu Lưu Ký' AND Author = N'Tô Hoài')
    INSERT INTO Book (Title, Author, Price, StockQuantity, [Description], ImageURL, CreatedAt, CategoryID)
    VALUES (N'Dế Mèn Phiêu Lưu Ký', N'Tô Hoài', 89000, 30, N'Tác phẩm thiếu nhi kinh điển Việt Nam về hành trình trưởng thành.', NULL, GETDATE(), @CatNovel);

    IF NOT EXISTS (SELECT 1 FROM Book WHERE Title = N'Tuổi Trẻ Đáng Giá Bao Nhiêu' AND Author = N'Rosie Nguyễn')
    INSERT INTO Book (Title, Author, Price, StockQuantity, [Description], ImageURL, CreatedAt, CategoryID)
    VALUES (N'Tuổi Trẻ Đáng Giá Bao Nhiêu', N'Rosie Nguyễn', 128000, 22, N'Sách truyền cảm hứng cho người trẻ về học tập, trải nghiệm và định hướng.', NULL, GETDATE(), @CatSkill);

    IF NOT EXISTS (SELECT 1 FROM Book WHERE Title = N'Sapiens: Lược Sử Loài Người' AND Author = N'Yuval Noah Harari')
    INSERT INTO Book (Title, Author, Price, StockQuantity, [Description], ImageURL, CreatedAt, CategoryID)
    VALUES (N'Sapiens: Lược Sử Loài Người', N'Yuval Noah Harari', 219000, 18, N'Khái quát lịch sử tiến hóa và phát triển của loài người qua nhiều thời kỳ.', NULL, GETDATE(), @CatTech);

    IF NOT EXISTS (SELECT 1 FROM Book WHERE Title = N'Nhà Giả Kim' AND Author = N'Paulo Coelho')
    INSERT INTO Book (Title, Author, Price, StockQuantity, [Description], ImageURL, CreatedAt, CategoryID)
    VALUES (N'Nhà Giả Kim', N'Paulo Coelho', 99000, 28, N'Tác phẩm nổi tiếng về hành trình theo đuổi ước mơ và tiếng gọi nội tâm.', NULL, GETDATE(), @CatPsychology);

    IF NOT EXISTS (SELECT 1 FROM Book WHERE Title = N'Cha Giàu Cha Nghèo' AND Author = N'Robert T. Kiyosaki')
    INSERT INTO Book (Title, Author, Price, StockQuantity, [Description], ImageURL, CreatedAt, CategoryID)
    VALUES (N'Cha Giàu Cha Nghèo', N'Robert T. Kiyosaki', 139000, 20, N'Bài học tài chính cá nhân cơ bản về tư duy tài sản và tự do tài chính.', NULL, GETDATE(), @CatBusiness);

    COMMIT TRANSACTION;

    SELECT COUNT(*) AS CategoryCount FROM Category;
    SELECT COUNT(*) AS BookCount FROM Book;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
