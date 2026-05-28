import { useEffect } from "react";
import { useCRMStore } from "../store/crmStore";

export function Toast() {
  const toasts = useCRMStore((state) => state.toasts);
  const removeToast = useCRMStore((state) => state.removeToast);

  useEffect(() => {
    if (!toasts.length) return;
    const timer = setTimeout(() => removeToast(toasts[0].id), 3200);
    return () => clearTimeout(timer);
  }, [toasts, removeToast]);

  return (
    <div className="toast-stack">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast toast-${toast.type}`}>
          {toast.message}
        </div>
      ))}
    </div>
  );
}
