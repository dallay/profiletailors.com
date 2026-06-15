import type { UnwrapRefCarouselApi as CarouselApi, CarouselEmits, CarouselProps } from './interface'
import { createInjectionState } from '@vueuse/core'
import emblaCarouselVue from 'embla-carousel-vue'
import { ref, watch } from 'vue'

const [useProvideCarousel, useInjectCarousel] = createInjectionState(
  ({ opts, orientation, plugins }: CarouselProps, emits: CarouselEmits) => {
    const [emblaNode, emblaApi] = emblaCarouselVue(
      {
        ...opts,
        axis: orientation === 'horizontal' ? 'x' : 'y',
      },
      plugins,
    )

    function scrollPrev() {
      emblaApi.value?.scrollPrev()
    }
    function scrollNext() {
      emblaApi.value?.scrollNext()
    }

    const canScrollNext = ref(false)
    const canScrollPrev = ref(false)

    function onSelect(api: CarouselApi) {
      canScrollNext.value = api?.canScrollNext() || false
      canScrollPrev.value = api?.canScrollPrev() || false
    }

    watch(emblaApi, (api) => {
      if (!api) return

      api.on('init', onSelect)
      api.on('reInit', onSelect)
      api.on('select', onSelect)

      emits('init-api', api)
    })

    return {
      carouselRef: emblaNode,
      carouselApi: emblaApi,
      canScrollPrev,
      canScrollNext,
      scrollPrev,
      scrollNext,
      orientation,
    }
  },
)

function useCarousel() {
  const carouselState = useInjectCarousel()

  if (!carouselState) throw new Error('useCarousel must be used within a <Carousel />')

  return carouselState
}

export { useCarousel, useProvideCarousel }
