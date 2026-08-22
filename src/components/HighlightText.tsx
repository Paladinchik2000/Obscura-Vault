import React from 'react';

export interface HighlightTextProps {
  text: string;
  query: string;
  isDarkMode?: boolean;
  className?: string;
  highlightClassName?: string;
  accentVariant?: 'crimson' | 'amber' | 'emerald';
}

/**
 * HighlightText Component
 * Replicates the Jetpack Compose buildAnnotatedString search highlighting pattern in React.
 * Dynamically parses text, locates case-insensitive query matches, and applies bold styling + accent coloring.
 */
export const HighlightText: React.FC<HighlightTextProps> = ({
  text,
  query,
  isDarkMode = true,
  className = '',
  highlightClassName = '',
  accentVariant = 'crimson',
}) => {
  if (!text) return null;
  const trimmedQuery = query.trim();
  if (!trimmedQuery) return <span className={className}>{text}</span>;

  // Escape special regex characters in query
  const escapedQuery = trimmedQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(`(${escapedQuery})`, 'gi');
  const parts = text.split(regex);

  const getHighlightStyles = () => {
    if (highlightClassName) return highlightClassName;

    if (accentVariant === 'amber') {
      return isDarkMode
        ? 'bg-amber-400/30 text-amber-200 ring-1 ring-amber-400/60 font-black rounded px-1 py-0.2 mx-0.5 transition-all shadow-xs'
        : 'bg-amber-200 text-amber-950 ring-1 ring-amber-500/70 font-black rounded px-1 py-0.2 mx-0.5 transition-all shadow-xs';
    }

    if (accentVariant === 'emerald') {
      return isDarkMode
        ? 'bg-emerald-500/30 text-emerald-200 ring-1 ring-emerald-400/60 font-black rounded px-1 py-0.2 mx-0.5 transition-all shadow-xs'
        : 'bg-emerald-200 text-emerald-950 ring-1 ring-emerald-500/70 font-black rounded px-1 py-0.2 mx-0.5 transition-all shadow-xs';
    }

    // Default: Obscura Crimson Accent
    return isDarkMode
      ? 'bg-obscura-crimson/30 text-white ring-1 ring-obscura-crimson/80 border-b-2 border-obscura-crimson font-black rounded px-1 py-0.2 mx-0.5 transition-all shadow-xs'
      : 'bg-red-100 text-red-900 ring-1 ring-red-400/80 border-b-2 border-red-500 font-black rounded px-1 py-0.2 mx-0.5 transition-all shadow-xs';
  };

  return (
    <span className={className}>
      {parts.map((part, index) => {
        if (part.toLowerCase() === trimmedQuery.toLowerCase()) {
          return (
            <mark
              key={index}
              className={getHighlightStyles()}
            >
              {part}
            </mark>
          );
        }
        return <React.Fragment key={index}>{part}</React.Fragment>;
      })}
    </span>
  );
};
