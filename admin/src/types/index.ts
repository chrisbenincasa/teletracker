// prettier-ignore
export type DeepReadonly<T> =
  T extends (infer R)[] ? DeepReadonlyArray<R> :
    T extends Function ? T :
      T extends object ? DeepReadonlyObject<T> :
        T;

export interface DeepReadonlyArray<T> extends ReadonlyArray<DeepReadonly<T>> {}

export type DeepReadonlyObject<T> = {
  readonly [P in keyof T]: DeepReadonly<T[P]>;
};

export type PotentialMatch = DeepReadonly<{
  id: string;
  created_at: string;
  last_updated: string;
  state: PotentialMatchState;
  potential: PotentialMatchStorageItem;
  scraped: PotentialMatchScrapedItem;
  availability?: ItemAvailability[];
}>;

export enum PotentialMatchState {
  Unmatched = 'unmatched',
  Matched = 'matched',
  NonMatch = 'nonmatch',
}

interface PotentialMatchStorageItem {
  id: string;
  original_title?: string;
  title: string;
  release_date?: string;
  external_ids: string;
  type: string;
  slug?: string;
  images?: {
    provider_id: number;
    provider_shortname: string;
    id: string;
    image_type: string;
  }[];
}

interface PotentialMatchScrapedItem {
  type: ScrapeItemType;
  item: {
    availableDate?: string;
    title: string;
    releaseYear?: number;
    network: string;
    status: string;
    externalId?: string;
    description?: string;
    itemType: string;
    url?: string;
    posterImageUrl?: string;
  };
  raw: object;
}

export interface ItemAvailability {
  readonly networkId: number;
  readonly networkName?: string;
  readonly offers: ItemAvailabilityOffer[];
}

export interface ItemAvailabilityOffer {
  readonly region: string;
  readonly startDate?: string;
  readonly endDate?: string;
  readonly offerType: OfferType;
  readonly cost?: number;
  readonly currency?: string;
  readonly presentationType?: PresentationType;
  readonly links?: ItemAvailabilityOfferLinks;
}

export interface ItemAvailabilityOfferLinks {
  readonly web?: string;
}

export type PresentationType = 'sd' | 'hd' | '4k';

export enum OfferType {
  buy = 'buy',
  rent = 'rent',
  theater = 'theater',
  subscription = 'subscription',
  free = 'free',
  ads = 'ads',
  aggregate = 'aggregate',
}

export enum ScrapeItemType {
  HuluCatalog = 'HuluCatalog',
  HboCatalog = 'HboCatalog',
  NetflixCatalog = 'NetflixCatalog',
  DisneyPlusCatalog = 'DisneyPlusCatalog',
  HboMaxCatalog = 'HboMaxCatalog',
  HboChanges = 'HboChanges',
  NetflixOriginalsArriving = 'NetflixOriginalsArriving',
}
