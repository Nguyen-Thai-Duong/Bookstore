-- Add Gender and DateOfBirth columns to User table
ALTER TABLE [User] 
ADD Gender VARCHAR(10) NULL,
    DateOfBirth DATE NULL;
