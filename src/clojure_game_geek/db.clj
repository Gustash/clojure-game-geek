(ns clojure-game-geek.db
  (:require
    [com.stuartsierra.component :as component]
    [io.pedestal.log :as log]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [buddy.hashers :as hs]
    [clj-time.core :as t]
    [buddy.sign.jwt :as jwt])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(defn ^:private pooled-data-source
  [host dbname user password port]
  {:datasource
   (doto (ComboPooledDataSource.)
     (.setDriverClass "org.postgresql.Driver")
     (.setJdbcUrl (str "jdbc:postgresql://" host ":" port "/" dbname))
     (.setUser user)
     (.setPassword password))})

(defrecord ClojureGameGeekDb [ds]

  component/Lifecycle

  (start [this]
    (assoc this
      :ds (pooled-data-source "localhost" "cggdb" "cgg_role" "lacinia" 25432)))

  (stop [this]
    (-> ds :datasource .close)
    (assoc this :ds nil)))

(defn new-db
  []
  {:db (map->ClojureGameGeekDb {})})

(defn ^:private query
  [component statement]
  (let [[sql & params] statement]
    (log/debug :sql (str/replace sql #"\s+" " ")
               :params params))
  (jdbc/query (:ds component) statement))

(defn ^:private execute
  [component statement]
  (let [[sql & params] statement]
    (log/debug :sql (str/replace sql #"\s+" " ")
               :params params))
  (jdbc/execute! (:ds component) statement))

(defn ^:private insert
  [component table row]
  (log/debug :table table
             :row row)
  (jdbc/insert! (:ds component) table row))

(defn ^:private valid-member-credentials?
  [component username password]
  (->> (query component
             ["select password
              from member
                where username = ?" username])
      first
      :password
      (hs/check password)))

(defn ^:private create-auth-token
  [member]
  (let [exp (t/plus (t/now) (t/days 1))]
    (jwt/sign {:member member :exp exp} "key")))

(defn find-game-by-id
  [component game-id]
  (first
    (query component
           ["select game_id, name, summary, min_players, max_players, created_at, updated_at
               from board_game where game_id = ?" game-id])))

(defn find-member-by-id
  [component member-id]
  (first
    (query component
           ["select member_id, username, created_at, updated_at
             from member
             where member_id = ?" member-id])))

(defn find-member-by-name
  [component username]
  (first
    (query component
           ["select member_id, username, created_at, updated_at
             from member
             where username = ?" username])))

(defn list-designers-for-game
  [component game-id]
  (query component
         ["select d.designer_id, d.name, d.uri, d.created_at, d.updated_at
           from designer d
           inner join designer_to_game j on (d.designer_id = j.designer_id)
           where j.game_id = ?
           order by d.name" game-id]))

(defn list-games-for-designer
  [component designer-id]
  (query component
         ["select g.game_id, g.name, g.summary, g.min_players, g.max_players, g.created_at, g.updated_at
           from board_game g
           inner join designer_to_game j on (g.game_id = j.game_id)
           where j.designer_id = ?
           order by g.name" designer-id]))

(defn list-ratings-for-game
  [component game-id]
  (query component
         ["select game_id, member_id, rating, created_at, updated_at
           from game_rating
           where game_id = ?" game-id]))

(defn list-ratings-for-member
  [component member-id]
  (query component
         ["select game_id, member_id, rating, created_at, updated_at
           from game_rating
           where member_id = ?" member-id]))

(defn upsert-game-rating
  "Adds a new game rating, or changes the value of an existing game rating.

  Returns nil"
  [component game-id member-id rating]
  (execute component
           ["insert into game_rating (game_id, member_id, rating)
             values (?, ?, ?)
             on conflict (game_id, member_id) do update set rating = ?" game-id member-id rating rating])
  nil)

(defn insert-member
  "Creates a new member.

  Returns the created Member"
  [component username password]
  (first
    (insert component :member {:username username :password (hs/derive password)})))

(defn login-member
  "Checks credentials for a member.

  Returns a token if the credentials are correct.
  Otherwise, returns null."
  [component username password]
  (let [valid? (valid-member-credentials? component username password)]
    (log/debug :username username :password password :hash (hs/derive password) :valid (valid-member-credentials? component username password))
    (cond
      valid?
      (create-auth-token (find-member-by-name component username))

      :else
      nil)))