// useDocumentTitle.js
import { useEffect } from "react";

const useDocumentTitle = (title) => {
  useEffect(() => {
    document.title = `${title} - ThermoSight TMS`;
  }, [title]);
};

export default useDocumentTitle;
