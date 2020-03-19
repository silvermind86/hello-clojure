(ns hello
 (:require [clojure.data.json :as json]                                              
           [io.pedestal.http :as http]
           [io.pedestal.http.route :as route]
           [io.pedestal.http.content-negotiation :as conneg]))


(def supported-types ["text/html" "application/edn" "application/json" "text/plain"]) 

(def content-neg-intc (conneg/negotiate-content supported-types))   

(def unmentionables #{"YHWH" "Voldemort" "Mxyzptlk" "Rumplestiltskin" "曹操"})

(defn accepted-type
  [context]
  (get-in context [:request :accept :field] "text/plain"))

(defn transform-content
  [body content-type]
  (case content-type
    "text/html"        body
    "text/plain"       body
    "application/edn"  (pr-str body)
    "application/json" (json/write-str body)))

(defn coerce-to
  [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def coerce-body
  {:name ::coerce-body
   :leave
   (fn [context]
     (if (get-in context [:response :headers "Content-Type"])
       context
       (update-in context [:response] coerce-to (accepted-type context))))})

(defn ok [body]
  {:status 200 :body body
   :headers {"Content-Type" "text/html"}})

(defn not-found []
  {:status 404 :body "Not found\n"
   :headers {"Content-Type" "text/html"}})

(def echo
  {:name ::echo
   :enter #(assoc % :response (ok (:request %)))})    

(defn- greeting-for [nome]
 (cond
  (unmentionables nome) nil
  (empty? nome)      "Hello, world!\n"
  :else              (str "Hello, " nome "\n")))

(defn respond-hello [request]
 (let [nome (get-in request [:query-params :nome])
       resp (greeting-for nome)]
  (if resp
      (ok resp)
      (not-found))))      

 (def routes
  (route/expand-routes                                   
   #{["/greet" :get [coerce-body content-neg-intc respond-hello] :route-name :greet]
     ["/echo"  :get echo]}))

(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8890})

(defn start []
  (http/start (http/create-server service-map)))

(defonce server (atom nil))                                                             

(defn start-dev []
  (reset! server                                                                        
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))                                   

(defn stop-dev []
  (http/stop @server))

(defn restart []                                                                        
  (stop-dev)
  (start-dev))