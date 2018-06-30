import { promisify } from 'util';
import { OfferType } from '../../src/db/entity';

export const timer = async (ms: number) => {
    return promisify(setTimeout)(ms);
};

export const looper = async function looper<T>(args: Iterator<[number, T]>, f: (x: T) => Promise<void>) {
    while (true) {
        let res = args.next();
        if (res.done) break;
        let [_, x] = res.value;
        await f(x);
        await timer(1000);
    }
}

export function getOfferType(s: string): OfferType | undefined {
    switch (s.toLowerCase()) {
        case 'buy': return OfferType.Buy;
        case 'rent': return OfferType.Rent;
        case 'theater': return OfferType.Theater;
        case 'cinema': return OfferType.Theater;
        case 'subscription': return OfferType.Subscription;
        case 'flatrate': return OfferType.Subscription;
        case 'free': return OfferType.Free;
        case 'ads': return OfferType.Ads;
        default:
            console.log('could not get offertype for type = ' + s);
            return undefined
    }
}

export interface JustWatchOffer {
    monetization_type: string,
    provider_id: number,
    retail_price: number,
    currency: string,
    presentation_type: string,
    date_created: string,
    country: string
}