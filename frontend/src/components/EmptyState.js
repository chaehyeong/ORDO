function EmptyState({ icon, title, description, actionLabel, onAction }) {
    return (
        <div className="program-empty">
            <div className="program-icon">{icon}</div>
            <p className="program-empty-title">{title}</p>
            <p className="program-empty-desc">{description}</p>
            <button type="button" className="btn-ordo" onClick={onAction}>{actionLabel}</button>
        </div>
    );
}

export default EmptyState;
