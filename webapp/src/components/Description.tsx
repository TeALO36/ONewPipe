import type { ReactNode } from 'react';

/**
 * Renders a video or channel description. YouTube descriptions come as HTML;
 * only text, line breaks and http(s) links are kept, so no markup from the
 * service can run in the app.
 */
export function Description({ text, format }: { text: string; format: 'html' | 'markdown' | 'text' }) {
  if (format !== 'html') {
    return <>{linkify(text)}</>;
  }
  const doc = new DOMParser().parseFromString(`<div>${text}</div>`, 'text/html');
  return <>{convert(doc.body.firstChild as Element)}</>;
}

function convert(node: Node, key = 'd'): ReactNode {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent;
  if (node.nodeType !== Node.ELEMENT_NODE) return null;
  const element = node as Element;
  const children = Array.from(element.childNodes).map((child, index) => convert(child, `${key}-${index}`));
  const tag = element.tagName.toLowerCase();
  if (tag === 'br') return <br key={key} />;
  if (tag === 'a') {
    const href = element.getAttribute('href') ?? '';
    if (/^https?:\/\//i.test(href)) {
      return (
        <a key={key} href={href} target="_blank" rel="noreferrer">
          {children}
        </a>
      );
    }
  }
  if (tag === 'p' || tag === 'div') {
    return (
      <span key={key}>
        {children}
        {tag === 'p' && <br />}
      </span>
    );
  }
  return <span key={key}>{children}</span>;
}

function linkify(text: string): ReactNode[] {
  return text.split(/(https?:\/\/[^\s]+)/g).map((part, index) =>
    /^https?:\/\//.test(part) ? (
      <a key={index} href={part} target="_blank" rel="noreferrer">
        {part}
      </a>
    ) : (
      part
    )
  );
}
