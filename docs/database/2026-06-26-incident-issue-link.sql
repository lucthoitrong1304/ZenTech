alter table incidents
    add column issue_signature varchar(1000) null,
    add column creation_source varchar(20) not null default 'MANUAL';
