-- name: sql-get-user
SELECT * FROM ansel_users WHERE email = :email;

-- name: sql-user-exists
SELECT COUNT(*) FROM ansel_users WHERE email = :email;

-- name: sql-user-count
SELECT COUNT(*) FROM ansel_users;

-- name: sql-insert-user!
INSERT INTO ansel_users
(password, email, name, admin)
VALUES (:password, :email, :name, :admin);

-- name: sql-insert-image!
INSERT INTO ansel_images
(filename, caption, focal_length, focal_length_35, shutter_speed, aperture, iso, exposure_compensation, user_id, captured)
VALUES
(:filename, :caption, :focal_length, :focal_length_35, :shutter_speed, :aperture, :iso, :exposure_compensation, :user_id, :captured)

-- name: sql-insert-image-to-album!
INSERT INTO ansel_image_album_rels
(image_id, album_id)
VALUES
(:image_id, :album_id)

-- name: sql-get-all-images
SELECT * FROM ansel_images ORDER BY created;

-- name: sql-get-images-limit
SELECT * FROM ansel_images ORDER BY created LIMIT :limit;

-- name: sql-get-image-by-id
SELECT ansel_images.*,
(SELECT COUNT(*) FROM ansel_likes WHERE ansel_likes.image_id = ansel_images.id) as num_likes,
(SELECT COUNT(*) FROM ansel_likes WHERE ansel_likes.image_id = ansel_images.id and ansel_likes.user_id = :user_id) as my_like
FROM ansel_images WHERE ansel_images.id = :id;

-- SELECT A.*, (SELECT COUNT(*) FROM B WHERE B.a_id = A.id) AS TOT FROM A

-- name: sql-insert-album!
INSERT INTO ansel_albums (name, slug, user_id) VALUES (:name, :slug, :user_id);

-- name: sql-get-all-albums
SELECT * FROM ansel_albums ORDER BY name;

-- name: sql-get-images-for-album
SELECT * FROM ansel_images
INNER JOIN ansel_image_album_rels as rels on (rels.image_id = ansel_images.id)
WHERE rels.album_id = :album;

-- name: sql-get-album-by-slug
SELECT * FROM ansel_albums WHERE slug = :slug;

-- name: sql-insert-like!
INSERT INTO ansel_likes (image_id, user_id) VALUES (:image, :user);

-- name: sql-like-exists
SELECT COUNT(*) FROM ansel_likes WHERE image_id = :image AND user_id = :user;

-- name: sql-insert-comment!
INSERT INTO ansel_comments
(image_id, user_id, content)
VALUES (:image, :user, :content);

-- name: sql-get-comments-for-image
SELECT * FROM ansel_comments WHERE image_id = :image;
