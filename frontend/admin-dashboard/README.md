This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.

## Cấu hình Thông báo & SMTP (Notification Settings)

Hệ thống hỗ trợ gửi thông báo qua Email khi xuất hiện các sự kiện khẩn cấp:
- **Blacklist Hit (Nguy hiểm)**: Phát hiện xe thuộc danh sách đen.
- **Trùng phiên đỗ (Nghi vấn)**: Biển số xe xuất hiện 2 lần cùng trạng thái đang đỗ.
- **Xe ra không có phiên vào**: Phát hiện xe đi ra nhưng không tìm thấy thông tin lúc vào.

### Hướng dẫn tích hợp SMTP Gmail:
1. Truy cập trang Cấu hình thông báo từ Sidebar điều hướng dành cho Admin.
2. Điền thông tin Host (`smtp.gmail.com`), Port (`587`), tài khoản Gmail.
3. Sử dụng Mật khẩu ứng dụng (App Password) được tạo từ tài khoản Google để bảo mật.
4. Nhập email nhận thông báo của người vận hành (Operator).
5. Click **Gửi thử** để kiểm tra kết nối SMTP trước khi lưu.
