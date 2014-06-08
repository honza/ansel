--  Images

CREATE table ansel_images (
    id integer NOT NULL,
    filename character varying(256) NOT NULL,
    created timestamp default current_timestamp,
    caption text,
    focal_length integer,
    focal_length_35 integer,
    shutter_speed character varying(50),
    aperture character varying(50),
    iso integer,
    exposure_compensation character varying(50),
    user_id integer NOT NULL,
    captured timestamp
);

CREATE SEQUENCE ansel_images_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE ansel_images_id_seq OWNED BY ansel_images.id;
ALTER TABLE ONLY ansel_images ALTER COLUMN id SET DEFAULT nextval('ansel_images_id_seq'::regclass);

--  Albums

CREATE table ansel_albums (
    id integer NOT NULL,
    created timestamp default current_timestamp,
    name character varying(256),
    slug character varying(256),
    user_id integer NOT NULL
);

CREATE SEQUENCE ansel_albums_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE ansel_albums_id_seq OWNED BY ansel_albums.id;
ALTER TABLE ONLY ansel_albums ALTER COLUMN id SET DEFAULT nextval('ansel_albums_id_seq'::regclass);

--  Image album relations

CREATE table ansel_image_album_rels (
    id integer NOT NULL,
    image_id integer NOT NULL,
    album_id integer NOT NULL
);

CREATE SEQUENCE ansel_image_album_rels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE ansel_image_album_rels_id_seq OWNED BY ansel_image_album_rels.id;
ALTER TABLE ONLY ansel_image_album_rels ALTER COLUMN id SET DEFAULT nextval('ansel_image_album_rels_id_seq'::regclass);

-- Likes

CREATE table ansel_likes (
    id integer NOT NULL,
    created timestamp default current_timestamp,
    user_id integer NOT NULL,
    image_id integer NOT NULL
);

CREATE SEQUENCE ansel_likes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE ansel_likes_id_seq OWNED BY ansel_likes.id;
ALTER TABLE ONLY ansel_likes ALTER COLUMN id SET DEFAULT nextval('ansel_likes_id_seq'::regclass);

-- Comments

CREATE table ansel_comments (
    id integer NOT NULL,
    created timestamp default current_timestamp,
    image_id integer NOT NULL,
    user_id integer NOT NULL,
    content text
);

CREATE SEQUENCE ansel_comments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE ansel_comments_id_seq OWNED BY ansel_comments.id;
ALTER TABLE ONLY ansel_comments ALTER COLUMN id SET DEFAULT nextval('ansel_comments_id_seq'::regclass);

-- Users

CREATE table ansel_users (
    id integer NOT NULL,
    password character varying(256) NOT NULL,
    email character varying(256) NOT NULL,
    name character varying(256),
    admin boolean default false,
    created timestamp default current_timestamp
);

CREATE SEQUENCE ansel_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE ansel_users_id_seq OWNED BY ansel_users.id;
ALTER TABLE ONLY ansel_users ALTER COLUMN id SET DEFAULT nextval('ansel_users_id_seq'::regclass);
