(ns rum.specs)

(def mixins
  #{:init :will-mount :before-render :wrap-render :did-mount
    :after-render :did-remount :will-remount :should-update :will-update
    :did-update :did-catch :will-unmount :child-context
    :class-properties :static-properties :key-fn})

(def deprecated-mixins
  {:did-remount ":did-remount is deprecated and was renamed to :will-remount, semantics didn't change, it was always called in componentWillReceiveProps"})
