import React, { Component } from 'react';
import { View, ScrollView, Image } from 'react-native';
import { Thing } from '../Model/external/themoviedb';
import { networks } from '../Components/Helpers/networks';
import getMetadata from './Helpers/getMetadata';
import { Card, CardContent, Title, ListAccordion, ListItem } from 'react-native-paper'

import styles from './Styles/GetAvailability';

interface Props {
    item?: Thing
}

export default class GetAvailability extends Component {
    constructor(props: Props) {
        super(props);
    }

    renderOfferTypes(offerTypes: any) {
        console.log(offerTypes);
        return (
            <ListItem title={`${offerTypes.offerType} for ${offerTypes.cost ? offerTypes.cost : 'FREE'}`} />
        )
    }

    renderAvailability(availability: any) {
        const costs = availability.costs;
        let offerTypes = [];

        availability.map(
            offerTypes.push(this.renderOfferTypes)
        );

        return (
            <View
                key={availability.networkId} 
                onStartShouldSetResponder={() => true}
                style={{flex:1}}
                >
                  <ListAccordion
                    title={availability.name}
                    icon={
                        <Image
                            source={
                                networks[availability.slug].preferredLogo
                            }
                            style={{
                                width: 24,
                                height: 24
                            }}
                        />
                    }
                    style={{flex: 1}}
                >
                    {offerTypes}
                </ListAccordion>
            </View>
        );
    }

    renderAvailabilities(availabilities: any) {
        const consolidation = availabilities.reduce(function(obj, item) {
            if (!obj[item.network.name]) {
                obj[item.network.name] = {
                    name: item.network.name,
                    costs: [{
                        offerType: item.offerType,
                        cost:  item.cost
                    }],
                    networkId: item.networkId,
                    slug: item.network.slug
                }
            } else {
                obj[item.network.name].costs.push({ 
                    offerType: item.offerType,
                    cost:  item.cost
                });
            }
            return obj;
        }, {});

        let whereToWatch = [];
        for(let key in consolidation) {
            if(consolidation.hasOwnProperty(key)) {
                whereToWatch.push(this.renderAvailability(consolidation[key]));
            }
        }
        return whereToWatch;
    }

    render () {
        return (
            getMetadata.getAvailabilityInfo(this.props.item) ? 
                <Card style={styles.castContainer}>
                    <CardContent>
                        <Title style={styles.castHeader}>
                            Where to Watch:
                        </Title>
                        <ScrollView style={styles.avatarContainer} >
                            { this.renderAvailabilities(this.props.item.availability) }
                        </ScrollView>
                    </CardContent>
                </Card>
            : null
        )
    }
};