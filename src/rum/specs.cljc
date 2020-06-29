(ns rum.specs)

(def mixins
  #{:init :will-mount :before-render :wrap-render :did-mount
    :after-render :did-remount :should-update :will-update
    :did-update :did-catch :will-unmount :child-context
    :class-properties :static-properties :key-fn})
