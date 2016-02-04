(ns rum.server-render-test
  (:require [clojure.test :refer :all]
            [rum.server-render :as sr]))

(deftest check-tag-match
  (is (= (sr/render-html [:div.header#up "test"])
        "<div id=\"up\" class=\"header\" data-reactid=\".0\" data-react-checksum=\"255398527\">test</div>")))

(deftest check-list
  (is (= (sr/render-html [:ul [:li {:key "F"}] [:li {:key "M"}]])
        "<ul data-reactid=\".0\" data-react-checksum=\"-1218569571\"><li data-reactid=\".0.$F\"></li><li data-reactid=\".0.$M\"></li></ul>")))

(deftest check-header
  (is (= (sr/render-html [:ul.nav__content
                          '([:li.menu-item {:key "F"} "Женщинам"]
                            [:li.menu-item {:key "M"} "Мужчинам"])
                          [:li.menu-item {:key "outlet"} "Outlet"]])
        "<ul class=\"nav__content\" data-reactid=\".0\" data-react-checksum=\"496011080\"><li class=\"menu-item\" data-reactid=\".0.0:$F\">Женщинам</li><li class=\"menu-item\" data-reactid=\".0.0:$M\">Мужчинам</li><li class=\"menu-item\" data-reactid=\".0.$outlet\">Outlet</li></ul>")))

(deftest check-campaign
  (is (= (sr/render-html
           [:div#today.content.wrapper
            '([:div.banner {:class " big ",
                            :style {:background-image "url(123)"},
                            :key "campaign-20871"}
               [:a.banner__item-link {:href "/catalogue/s-10079-colin-s/"}]]
              [:div.banner {:class " ", :key "banner-:promo"}
               [:a.banner__item-link {:href nil, :target "_blank"}]]
              [:div.banner {:class " medium ", :style {:background-image "url(321)"},
                            :key "campaign-20872"}
               [:a.banner__item-link {:href "/catalogue/s-10089-rinascimento/"}]])])
        "<div id=\"today\" class=\"content wrapper\" data-reactid=\".0\" data-react-checksum=\"-1202270818\"><div style=\"background-image:url(123);\" class=\"banner  big \" data-reactid=\".0.$campaign-20871\"><a href=\"/catalogue/s-10079-colin-s/\" class=\"banner__item-link\" data-reactid=\".0.$campaign-20871.0\"></a></div><div class=\"banner  \" data-reactid=\".0.$banner-=2promo\"><a target=\"_blank\" class=\"banner__item-link\" data-reactid=\".0.$banner-=2promo.0\"></a></div><div style=\"background-image:url(321);\" class=\"banner  medium \" data-reactid=\".0.$campaign-20872\"><a href=\"/catalogue/s-10089-rinascimento/\" class=\"banner__item-link\" data-reactid=\".0.$campaign-20872.0\"></a></div></div>")))
