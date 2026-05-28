"use client";

import { useNotificationStore } from "@/store/notification-store";
import { X, CheckCircle2, AlertCircle, Info } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";

const iconMap = {
  success: <CheckCircle2 className="h-4 w-4 text-emerald-400" />,
  error: <AlertCircle className="h-4 w-4 text-rose-400" />,
  warning: <AlertCircle className="h-4 w-4 text-amber-400" />,
  info: <Info className="h-4 w-4 text-cyan-400" />,
};

const borderMap = {
  success: "border-emerald-500/30",
  error: "border-rose-500/30",
  warning: "border-amber-500/30",
  info: "border-cyan-500/30",
};

export function ToastContainer() {
  const { toasts, removeToast } = useNotificationStore();
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      <AnimatePresence>
        {toasts.map((t) => (
          <motion.div
            key={t.id}
            initial={{ opacity: 0, y: 20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 10, scale: 0.95 }}
            transition={{ duration: 0.2 }}
            className={`flex items-center gap-3 rounded-xl border bg-slate-900 px-4 py-3 shadow-xl ${borderMap[t.type]} min-w-[280px] max-w-sm`}
          >
            {iconMap[t.type]}
            <span className="flex-1 text-sm text-slate-200">{t.message}</span>
            <button onClick={() => removeToast(t.id)} className="text-slate-500 hover:text-slate-300">
              <X className="h-3.5 w-3.5" />
            </button>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
