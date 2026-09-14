import { useEffect, useRef, useState } from 'react';
import { BellIcon, CheckIcon, CloseIcon, ErrorIcon } from '../icons';
import { notifications, useNotifications } from '../notifications';
import { LinearWavyProgress } from './WavyProgress';

export function NotificationCenter() {
  const { tasks, unseen } = useNotifications();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    notifications.markSeen();
    const close = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open, tasks.length]);

  const running = tasks.filter((t) => t.status === 'running').length;

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        type="button"
        className="icon-button"
        aria-label={`Notifications${unseen ? `, ${unseen} new` : ''}`}
        aria-expanded={open}
        title="Notifications"
        onClick={() => setOpen((value) => !value)}
      >
        <BellIcon />
        {unseen > 0 && !open && <span className="badge">{unseen > 9 ? '9+' : unseen}</span>}
      </button>

      {open && (
        <div className="popover notifications" role="dialog" aria-label="Notifications">
          <header>
            <span>Notifications{running ? ` · ${running} in progress` : ''}</span>
            {tasks.some((t) => t.status !== 'running') && (
              <button type="button" className="button" style={{ height: 30 }} onClick={() => notifications.clearFinished()}>
                Clear
              </button>
            )}
          </header>

          {tasks.length === 0 ? (
            <div className="empty-state">
              <BellIcon size={32} />
              <div>Downloads and other tasks show up here.</div>
            </div>
          ) : (
            tasks.map((task) => (
              <div key={task.id} className={`notification ${task.status}`}>
                <div className="title">
                  {task.status === 'done' && <CheckIcon size={16} style={{ verticalAlign: -3, marginRight: 6, color: 'var(--secondary)' }} />}
                  {task.status === 'failed' && <ErrorIcon size={16} style={{ verticalAlign: -3, marginRight: 6 }} />}
                  {task.title}
                </div>
                {task.status === 'running' && task.cancel ? (
                  <button type="button" className="icon-button" aria-label={`Cancel ${task.title}`} onClick={task.cancel}>
                    <CloseIcon size={18} />
                  </button>
                ) : task.status !== 'running' ? (
                  <button type="button" className="icon-button" aria-label={`Dismiss ${task.title}`} onClick={() => notifications.dismiss(task.id)}>
                    <CloseIcon size={18} />
                  </button>
                ) : (
                  <span />
                )}
                {task.detail && <div className="detail">{task.detail}</div>}
                {task.status === 'running' && <LinearWavyProgress value={task.progress} label={task.title} />}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
