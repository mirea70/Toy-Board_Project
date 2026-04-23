create table article (
                         article_id bigint not null primary key,
                         title varchar(100) not null,
                         content varchar(3000) not null,
                         board_id bigint not null,
                         writer_id bigint not null,
                         created_at datetime not null,
                         modified_at datetime not null
);

create index idx_board_id_article_id on article(board_id asc, article_id desc);

create table board_article_count (
                                     board_id bigint not null primary key,
                                     article_count bigint not null
);

create table article_like (
                              article_like_id bigint not null primary key,
                              article_id bigint not null,
                              user_id bigint not null,
                              created_at datetime not null
);

create unique index idx_article_id_user_id on article_like(article_id asc, user_id asc);

create table article_like_count (
                                    article_id bigint not null primary key,
                                    like_count bigint not null,
                                    version bigint not null
);

create table article_view_count (
                                    article_id bigint not null primary key,
                                    view_count bigint not null
);

create table comment (
                         comment_id bigint not null primary key,
                         content varchar(3000) not null,
                         article_id bigint not null,
                         parent_comment_id bigint not null,
                         writer_id bigint not null,
                         deleted bool not null,
                         created_at datetime not null
);

create table comment_v2 (
                            comment_id bigint not null primary key,
                            content varchar(3000) not null,
                            article_id bigint not null,
                            writer_id bigint not null,
                            path varchar(25) character set utf8mb4 collate utf8mb4_bin not null, deleted bool not null,
                            created_at datetime not null
);

create table article_comment_count (
                                       article_id bigint not null primary key,
                                       comment_count bigint not null
);

create table outbox (
                        outbox_id  bigint   not null primary key,
                        event_type varchar(50) not null,
                        payload    longtext not null,
                        shard_key  bigint   not null,
                        created_at datetime not null
);
create index idx_outbox_shard_key_created_at on outbox(shard_key asc, created_at asc);