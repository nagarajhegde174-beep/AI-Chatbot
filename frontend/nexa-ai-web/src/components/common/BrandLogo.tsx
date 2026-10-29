export function BrandLogo({
  size = 'md',
  variant = 'symbol',
  onClick,
}: {
  readonly size?: 'sm' | 'md' | 'lg'
  readonly variant?: 'symbol' | 'avatar'
  readonly onClick?: () => void
}) {
  const iconSizes = { sm: 22, md: 28, lg: 36 }
  const fontSizes = { sm: '1rem', md: '1.2rem', lg: '1.5rem' }

  return (
    <div
      className="nexa-brand-logo"
      style={{ fontSize: fontSizes[size], cursor: onClick ? 'pointer' : 'default' }}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={onClick ? (e) => e.key === 'Enter' && onClick() : undefined}
    >
      {variant === 'avatar' ? (
        <img
          src="/assets/nexa-avatar.png"
          alt="NexaAI"
          style={{
            width: iconSizes[size],
            height: iconSizes[size],
            borderRadius: '8px',
            objectFit: 'cover',
            border: '1px solid var(--border-subtle)',
          }}
        />
      ) : (
        <div
          className="nexa-brand-symbol"
          style={{ width: iconSizes[size], height: iconSizes[size] }}
          aria-hidden="true"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
            style={{ width: '65%', height: '65%' }}
          >
            <circle cx="12" cy="12" r="3" fill="currentColor" />
            <path d="M12 3v3" />
            <path d="M12 18v3" />
            <path d="M3 12h3" />
            <path d="M18 12h3" />
            <circle cx="12" cy="12" r="8" strokeOpacity="0.4" strokeDasharray="3 3" />
          </svg>
        </div>
      )}
      <span className="nexa-brand-text">
        Nexa<span className="nexa-brand-ai">AI</span>
      </span>
    </div>
  )
}
