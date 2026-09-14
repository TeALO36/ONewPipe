import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { CloseIcon, HistoryIcon, SearchIcon } from '../icons';
import { library, useLibrary } from '../library';
import { navigate } from '../router';

export function SearchBox({ initialQuery }: { initialQuery: string }) {
  const { searchHistory } = useLibrary();
  const [value, setValue] = useState(initialQuery);
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => setValue(initialQuery), [initialQuery]);

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!boxRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const suggestions = searchHistory
    .filter((query) => query.toLowerCase().includes(value.trim().toLowerCase()))
    .slice(0, 8);

  const submit = (query: string) => {
    const trimmed = query.trim();
    if (!trimmed) return;
    library.recordSearch(trimmed);
    setValue(trimmed);
    setOpen(false);
    inputRef.current?.blur();
    navigate({ name: 'search', query: trimmed, filter: 'all' });
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    submit(highlight >= 0 && suggestions[highlight] ? suggestions[highlight] : value);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setHighlight((h) => Math.min(suggestions.length - 1, h + 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlight((h) => Math.max(-1, h - 1));
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  };

  return (
    <div className="search" ref={boxRef}>
      <form role="search" onSubmit={onSubmit}>
        <input
          ref={inputRef}
          value={value}
          placeholder="Search videos and channels"
          aria-label="Search"
          onChange={(event) => {
            setValue(event.target.value);
            setHighlight(-1);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
        {value && (
          <button
            type="button"
            className="icon-button"
            aria-label="Clear search"
            onClick={() => {
              setValue('');
              inputRef.current?.focus();
            }}
          >
            <CloseIcon size={20} />
          </button>
        )}
        <button type="submit" className="icon-button" aria-label="Search">
          <SearchIcon />
        </button>
      </form>

      {open && suggestions.length > 0 && (
        <ul className="search-dropdown" role="listbox">
          {suggestions.map((query, index) => (
            <li
              key={query}
              role="option"
              aria-selected={index === highlight}
              className={index === highlight ? 'highlighted' : ''}
              onMouseDown={(event) => {
                event.preventDefault();
                submit(query);
              }}
            >
              <HistoryIcon size={20} className="muted" />
              <span className="query">{query}</span>
              <button
                type="button"
                className="icon-button"
                aria-label={`Remove ${query} from search history`}
                onMouseDown={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  library.removeSearch(query);
                }}
              >
                <CloseIcon size={18} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
