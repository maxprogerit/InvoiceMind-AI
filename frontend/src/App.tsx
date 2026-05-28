import { RouterProvider } from "react-router-dom";
import { useEffect } from "react";
import { router } from "./router";
import { useCRMStore } from "./store/crmStore";

export default function App() {
  const theme = useCRMStore((state) => state.settings.theme);

  useEffect(() => {
    document.body.dataset.theme = theme;
  }, [theme]);

  return <RouterProvider router={router} />;
}
