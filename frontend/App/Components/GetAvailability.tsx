import React, { Component } from 'react';
import { View, ScrollView, Image, Text } from 'react-native';
import { Thing } from '../Model/external/themoviedb';
import { networks } from '../Components/Helpers/networks';
import getMetadata from './Helpers/getMetadata';
import { Card, CardContent, Title, ListAccordion, ListItem, Chip } from 'react-native-paper'

import styles from './Styles/GetAvailability';

interface Props {
    item?: Thing
}

export default class GetAvailability extends Component {
    constructor(props: Props) {
        super(props);
    }

    renderCosts(offerTypes: any) {
        return (
            <Chip>{`${offerTypes.cost ? offerTypes.cost : 'FREE'}`}</Chip>
        )
    }

    renderOfferTypes(offerTypes: any) {
        return (
            <ListItem title={offerTypes.offerType} description={this.renderCosts(offerTypes)}/>
        )
    }

    renderAvailability(availability: any) {
        const costs = availability.costs;
        let offerTypes = [];

        for(let key in costs) {
            if (costs.hasOwnProperty(key)) {
                offerTypes.push(this.renderOfferTypes(costs[key]));
            }
        }

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

            
            

            // If the network doesn't exist in the object, add it
            if (!obj[item.network.name]) {
                // console.log("1");

                obj[item.network.name] = {
                    name: item.network.name,
                    costs: [{
                        offerType: item.offerType,
                        cost:  [item.cost]
                    }],
                    networkId: item.networkId,
                    slug: item.network.slug
                }

            // If the network exists and the offerType exists, add the new price (used for HD/SD)
            // } else if (result) {
                // console.log("2");
                
                // obj[item.network.name].costs.
                // offerType[item.offerType].push(item.cost);

            // If the network exists and the offerType doesn't exist, add the new offerType
            } else {
                let result = obj[item.network.name].costs.findIndex( i => i.offerType === item.offerType);
                console.log('results ', result);

                if (result != -1) {
                    obj[item.network.name].costs[result].cost.push(item.cost);
                    obj[item.network.name].costs[result].cost.sort();
                } else {
                    obj[item.network.name].costs.push({
                        offerType: item.offerType,
                        cost:  [item.cost]
                    });
                }
                
            }
            // console.log(obj);
            return obj;
        }, {});

        let whereToWatch = [];
        for(let key in consolidation) {
            if (consolidation.hasOwnProperty(key)) {
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