import React, { Component } from 'react';
import { Text, View, ScrollView } from 'react-native';
import { Avatar } from 'react-native-elements';
import { Thing } from '../Model/external/themoviedb';
import getMetadata from './Helpers/getMetadata';
import { parseInitials } from './Helpers/textHelper';
import { Card, CardContent, Title } from 'react-native-paper'

import styles from './Styles/GetCast';

interface Props {
    item?: Thing
}

export default class GetCast extends Component {
    constructor(props: Props) {
        super(props);
    }

    render () {
        return (
            getMetadata.getCast(this.props.item) ?
                <Card style={styles.castContainer}>
                    <CardContent>
                        <Title style={styles.castHeader}>Cast:</Title>
                        <ScrollView
                            pagingEnabled
                            horizontal={true}
                            showsHorizontalScrollIndicator={true}
                            style={styles.avatarContainer}
                        >
                            {
                                getMetadata.getCast(this.props.item).map((i) => (
                                    <View
                                        key={i.id}
                                        style={{
                                            flex:1,
                                            marginRight: 3
                                        }}
                                        onStartShouldSetResponder={() => true}
                                    >
                                        <Avatar
                                            key={i.id}
                                            large
                                            rounded
                                            source={
                                                i.profile_path ? { 
                                                    uri: "https://image.tmdb.org/t/p/w92" + i.profile_path
                                                } : null
                                            }
                                            activeOpacity={0.7}
                                            title={
                                                i.poster_path ? null : parseInitials(i.name)
                                            }
                                            titleStyle={
                                                parseInitials(i.name).length > 2 ? { fontSize: 26 } : null
                                            }

                                        />
                                        <Text style={styles.castName}>{i.name}</Text>
                                        <Text style={styles.castCharacter}>{i.character}</Text>
                                    </View>
                                ))
                            }
                        </ScrollView>
                    </CardContent>
                </Card>
            : null
        )
    }
};