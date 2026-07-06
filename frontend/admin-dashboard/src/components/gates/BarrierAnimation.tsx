"use client";

import { Car } from "lucide-react";

/**
 * Minh họa barie đóng/mở để quan sát trực quan khi CHƯA có camera bãi xe thật.
 * Cánh barie nâng lên (mở) / hạ ngang chắn đường (đóng) với CSS transition mượt.
 * `status` lấy từ gate (poll mỗi 10s) nên cánh tự cập nhật gần real-time.
 */
export function BarrierAnimation({
  status,
  direction,
}: {
  status: string;
  direction: string;
}) {
  const open = status.toUpperCase() === "OPEN";

  return (
    <div className="relative h-36 w-full overflow-hidden rounded-lg bg-gradient-to-b from-slate-100 to-slate-300">
      {/* Nhãn hướng */}
      <span className="absolute left-3 top-3 z-10 rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-medium text-white">
        {direction === "IN" ? "Cổng VÀO" : "Cổng RA"}
      </span>

      {/* Đèn tín hiệu + nhãn trạng thái */}
      <div className="absolute right-3 top-3 z-10 flex items-center gap-1.5">
        <span
          className={`h-3 w-3 rounded-full transition-colors duration-300 ${
            open
              ? "bg-success shadow-[0_0_8px_2px_rgba(34,197,94,0.55)]"
              : "bg-destructive shadow-[0_0_8px_2px_rgba(239,68,68,0.55)]"
          }`}
        />
        <span
          className={`text-xs font-semibold ${
            open ? "text-success" : "text-destructive"
          }`}
        >
          {open ? "Đang mở" : "Đang chắn"}
        </span>
      </div>

      {/* Mặt đường + vạch kẻ */}
      <div className="absolute bottom-0 left-0 right-0 h-12 bg-slate-600">
        <div className="absolute left-0 right-0 top-1/2 flex -translate-y-1/2 justify-center gap-3">
          {Array.from({ length: 9 }).map((_, i) => (
            <span key={i} className="h-[3px] w-3.5 bg-amber-200" />
          ))}
        </div>
      </div>

      {/* Ô tô tiến vào (bên trái cánh) */}
      <Car className="absolute bottom-2 left-5 h-9 w-9 text-white" />

      {/* Trụ barie + hộp điều khiển (cố định bên phải) */}
      <div
        className="absolute w-3 rounded-sm bg-slate-400"
        style={{ right: 64, bottom: 48, height: 64 }}
      />
      <div
        className="absolute h-3.5 w-6 rounded-sm bg-slate-700"
        style={{ right: 58, bottom: 40 }}
      />

      {/* Cánh barie: pivot tại trụ (mép phải), vươn sang trái chắn đường */}
      <div
        className="absolute flex origin-right items-center transition-transform duration-700 ease-in-out"
        style={{
          right: 70,
          bottom: 108,
          transform: open ? "rotate(-83deg)" : "rotate(0deg)",
        }}
      >
        {/* Thanh sọc đỏ-trắng (đầu tự do) */}
        <div
          className="h-3 w-24 rounded-sm"
          style={{
            background:
              "repeating-linear-gradient(90deg,#dc2626 0 14px,#ffffff 14px 28px)",
          }}
        />
        {/* Đối trọng tại pivot */}
        <div className="h-4 w-3.5 rounded-sm bg-slate-800" />
      </div>
    </div>
  );
}
