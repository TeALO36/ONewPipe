import { useEffect, useState } from 'react';

type Listener = (message: string) => void;
const listeners = new Set<Listener>();

/** Shows a short confirmation at the bottom of the screen. */
export function toast(message: string) {
  listeners.forEach((listener) => listener(message));
}

export function Toaster() {
  const [message, setMessage] = useState<string | null>(null);
  useEffect(() => {
    let timer: number | undefined;
    const listener: Listener = (text) => {
      setMessage(text);
      window.clearTimeout(timer);
      timer = window.setTimeout(() => setMessage(null), 2800);
    };
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
      window.clearTimeout(timer);
    };
  }, []);
  if (!message) return null;
  return (
    <div className="toast" role="status">
      {message}
    </div>
  );
}
