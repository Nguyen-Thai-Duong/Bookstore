SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    -- 1) Add 2 new categories (idempotent)
    IF NOT EXISTS (SELECT 1 FROM Category WHERE CategoryName = N'Business')
    BEGIN
        INSERT INTO Category (CategoryName, Description)
        VALUES (N'Business', N'Sach ve kinh doanh va quan tri');
    END;

    IF NOT EXISTS (SELECT 1 FROM Category WHERE CategoryName = N'Psychology')
    BEGIN
        INSERT INTO Category (CategoryName, Description)
        VALUES (N'Psychology', N'Sach ve tam ly hoc va phat trien ban than');
    END;

    DECLARE @BusinessId BIGINT = (SELECT TOP 1 CategoryID FROM Category WHERE CategoryName = N'Business');
    DECLARE @PsychologyId BIGINT = (SELECT TOP 1 CategoryID FROM Category WHERE CategoryName = N'Psychology');

    DECLARE @Books TABLE (
        Title NVARCHAR(200),
        Author NVARCHAR(100),
        Price DECIMAL(18,2),
        StockQuantity INT,
        Description NVARCHAR(500),
        CategoryKey NVARCHAR(10)
    );

    -- 2) Add 27 books distributed across existing 3 categories + 2 new categories
    INSERT INTO @Books (Title, Author, Price, StockQuantity, Description, CategoryKey)
    VALUES
    (N'Khong Gia Dinh', N'Hector Malot', 89000, 25, N'Tieu thuyet kinh dien', N'C1'),
    (N'Nhung Nguoi Kho Kho', N'Victor Hugo', 129000, 20, N'Tac pham van hoc noi tieng', N'C1'),
    (N'Don Kihote', N'Miguel de Cervantes', 119000, 18, N'Tieu thuyet phieu luu co dien', N'C1'),
    (N'Pride and Prejudice', N'Jane Austen', 99000, 30, N'Tieu thuyet tinh cam kinh dien', N'C1'),
    (N'The Great Gatsby', N'F. Scott Fitzgerald', 105000, 22, N'Tieu thuyet hien dai kinh dien', N'C1'),
    (N'1984', N'George Orwell', 98000, 28, N'Tieu thuyet phan dia tuong', N'C1'),

    (N'7 Habits of Highly Effective People', N'Stephen R. Covey', 145000, 35, N'Sach ky nang phat trien ban than', N'C2'),
    (N'Atomic Habits', N'James Clear', 155000, 40, N'Xay dung thoi quen tich cuc', N'C2'),
    (N'Deep Work', N'Cal Newport', 138000, 24, N'Ren luyen su tap trung sau', N'C2'),
    (N'The Power of Now', N'Eckhart Tolle', 132000, 27, N'Song trong hien tai', N'C2'),
    (N'Mindset', N'Carol S. Dweck', 128000, 21, N'Tu duy phat trien', N'C2'),
    (N'Ikigai', N'Hector Garcia', 118000, 29, N'Tim y nghia cuoc song', N'C2'),

    (N'Clean Code', N'Robert C. Martin', 210000, 15, N'Nguyen tac viet ma sach', N'C3'),
    (N'Design Patterns', N'GoF', 245000, 12, N'Mau thiet ke phan mem kinh dien', N'C3'),
    (N'Refactoring', N'Martin Fowler', 235000, 10, N'Cai to ma nguon an toan', N'C3'),
    (N'Spring in Action', N'Craig Walls', 265000, 14, N'Huong dan Spring thuc chien', N'C3'),
    (N'Effective Java', N'Joshua Bloch', 255000, 16, N'Best practices cho Java', N'C3'),

    (N'The Lean Startup', N'Eric Ries', 165000, 20, N'Khoi nghiep tinh gon', N'BUS'),
    (N'Zero to One', N'Peter Thiel', 172000, 19, N'Tu 0 den 1 trong khoi nghiep', N'BUS'),
    (N'Good to Great', N'Jim Collins', 188000, 17, N'Xay dung doanh nghiep vuot troi', N'BUS'),
    (N'The Hard Thing About Hard Things', N'Ben Horowitz', 195000, 14, N'Quan tri trong thoi ky kho khan', N'BUS'),
    (N'Blue Ocean Strategy', N'W. Chan Kim', 182000, 16, N'Chien luoc dai duong xanh', N'BUS'),

    (N'Thinking, Fast and Slow', N'Daniel Kahneman', 198000, 13, N'Tu duy nhanh va cham', N'PSY'),
    (N'Man''s Search for Meaning', N'Viktor E. Frankl', 142000, 18, N'Di tim le song', N'PSY'),
    (N'The Body Keeps the Score', N'Bessel van der Kolk', 205000, 12, N'Hieu ve sang chan tam ly', N'PSY'),
    (N'Emotional Intelligence', N'Daniel Goleman', 176000, 20, N'Tri tue cam xuc', N'PSY'),
    (N'Status Anxiety', N'Alain de Botton', 159000, 15, N'Noi lo au ve vi the xa hoi', N'PSY');

    ;WITH BookWithCategory AS (
        SELECT
            b.Title,
            b.Author,
            b.Price,
            b.StockQuantity,
            b.Description,
            CASE
                WHEN b.CategoryKey = N'C1' THEN CAST(1 AS BIGINT)
                WHEN b.CategoryKey = N'C2' THEN CAST(2 AS BIGINT)
                WHEN b.CategoryKey = N'C3' THEN CAST(3 AS BIGINT)
                WHEN b.CategoryKey = N'BUS' THEN @BusinessId
                WHEN b.CategoryKey = N'PSY' THEN @PsychologyId
                ELSE NULL
            END AS CategoryID
        FROM @Books b
    )
    INSERT INTO Book (Title, Author, Price, StockQuantity, Description, ImageURL, CreatedAt, CategoryID)
    SELECT
        bwc.Title,
        bwc.Author,
        bwc.Price,
        bwc.StockQuantity,
        bwc.Description,
        NULL,
        GETDATE(),
        bwc.CategoryID
    FROM BookWithCategory bwc
    WHERE bwc.CategoryID IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM Book x
          WHERE x.Title = bwc.Title
            AND ISNULL(x.Author, N'') = ISNULL(bwc.Author, N'')
      );

    COMMIT TRANSACTION;

    SELECT COUNT(*) AS CategoryCount FROM Category;
    SELECT COUNT(*) AS BookCount FROM Book;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    THROW;
END CATCH;
