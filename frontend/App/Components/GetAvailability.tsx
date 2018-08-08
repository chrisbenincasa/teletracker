import React, { Component } from 'react';
import { Text, View, ScrollView, Image } from 'react-native';
import { Button, Badge } from 'react-native-elements';
import { Thing } from '../Model/external/themoviedb';
import { networks } from '../Components/Helpers/networks';
import getMetadata from './Helpers/getMetadata';
import { Card } from 'react-native-paper'

import styles from './Styles/GetAvailability';

interface Props {
    item?: Thing
}

export default class GetAvailability extends Component {
    constructor(props: Props) {
        super(props);
    }

    renderAvailability(availability: any) {
        return (
            <View key={availability.id}>
                <Button
                    title={availability.network.name} />
                <Image
                    source={networks[availability.network.slug].preferredLogo}
                />
                <Badge
                    value={`${availability.offerType} for ${availability.cost}`}
                    textStyle={{ color: 'white' }}
                    wrapperStyle={{
                        marginHorizontal: 2,
                        marginVertical: 5
                    }}
                />
            </View>
        );
    }

    renderAvailabilities(availabilities: any[]) {
        // Sort by offer type & our network preference
        // To do: factor in users current settings (e.g. what they are subscribed to)
        // default sort: free, subscription, rent, ads, buy, theater, aggregate
        const offerTypeSort = {
            'free': 0,
            'subscription': 1,
            'rent': 2,
            'ads': 3,
            'buy': 4,
            'theater': 5,
            'aggregate': 6
        };

        const sortedAvailibility = availabilities.sort((a, b) => {
            // If an unknown offer type comes though, add it to end of list
            let offerTypeA = offerTypeSort[a.offerType] ? offerTypeSort[a.offerType] : Object.keys(offerTypeSort).length + 1;
            let offerTypeB = offerTypeSort[b.offerType] ? offerTypeSort[b.offerType] : Object.keys(offerTypeSort).length + 1;
            return offerTypeA - offerTypeB || networks[a.network.slug].sort  - networks[b.network.slug].sort;
        });

        return sortedAvailibility.map(this.renderAvailability);
    }

    render () {
        return (
            getMetadata.getAvailabilityInfo(this.props.item) ? 
                <Card style={styles.castContainer}>
                    <Text style={styles.castHeader}>Where to Watch:</Text>
                    <ScrollView
                        horizontal={true}
                        showsHorizontalScrollIndicator={false}
                        style={styles.avatarContainer}>
                        {this.renderAvailabilities(this.props.item.availability)}
                    </ScrollView>
                </Card>
            : null
        )
    }
};